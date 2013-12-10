package controllers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import models.App;
import models.Record;
import models.Space;
import models.User;
import models.Visualization;

import org.bson.types.ObjectId;

import play.data.Form;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Security;
import utils.ModelConversion.ConversionException;
import utils.search.SearchException;
import utils.search.SearchResult;
import utils.search.Search;
import views.html.elements.recordsearchresults;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mongodb.BasicDBList;

import controllers.forms.SpaceForm;

@Security.Authenticated(Secured.class)
public class Spaces extends Controller {

	public static Result show() {
		return show(Form.form(SpaceForm.class), null);
	}

	public static Result show(String activeSpaceId) {
		return show(Form.form(SpaceForm.class), new ObjectId(activeSpaceId));
	}

	public static Result show(Form<SpaceForm> spaceForm, ObjectId activeSpace) {
		ObjectId userId = new ObjectId(request().username());
		List<Record> records;
		List<Space> spaces;
		List<App> apps;
		try {
			records = new ArrayList<Record>(Record.findVisible(userId));
			spaces = new ArrayList<Space>(Space.findOwnedBy(userId));
			apps = new ArrayList<App>(User.findApps(userId));
		} catch (ConversionException e) {
			return internalServerError(e.getMessage());
		}
		Collections.sort(records);
		Collections.sort(spaces);
		Collections.sort(apps);
		return ok(views.html.spaces.render(spaceForm, records, spaces, activeSpace, apps, userId));
	}

	public static Result add() {
		Form<SpaceForm> spaceForm = Form.form(SpaceForm.class).bindFromRequest();
		if (spaceForm.hasErrors()) {
			return show(spaceForm, null);
		}

		SpaceForm form = spaceForm.get();
		Space newSpace = new Space();
		newSpace.name = form.name;
		newSpace.owner = new ObjectId(request().username());
		newSpace.visualization = new ObjectId(form.visualization);
		newSpace.records = new BasicDBList();
		String errorMessage;
		try {
			errorMessage = Space.add(newSpace);
		} catch (ConversionException e) {
			return internalServerError(e.getMessage());
		} catch (SearchException e) {
			return internalServerError(e.getMessage());
		}
		if (errorMessage != null) {
			spaceForm.reject(errorMessage);
			return show(spaceForm, null);
		}

		// TODO (?) js ajax insertion, open newly added space
		// return ok(space.render(newSpace));
		return redirect(routes.Spaces.show(newSpace._id.toString()));
	}

	public static Result rename(String spaceId) {
		// can't pass parameter of type ObjectId, using String
		ObjectId id = new ObjectId(spaceId);
		if (Secured.isOwnerOfSpace(id)) {
			String newName = Form.form().bindFromRequest().get("name");
			try {
				String errorMessage = Space.rename(id, newName);
				if (errorMessage == null) {
					return ok(newName);
				} else {
					return badRequest(errorMessage);
				}
			} catch (SearchException e) {
				return internalServerError(e.getMessage());
			}
		} else {
			return forbidden();
		}
	}

	public static Result delete(String spaceId) {
		// can't pass parameter of type ObjectId, using String
		ObjectId id = new ObjectId(spaceId);
		if (Secured.isOwnerOfSpace(id)) {
			String errorMessage = Space.delete(id);
			if (errorMessage == null) {
				return ok(routes.Application.spaces().url());
			} else {
				return badRequest(errorMessage);
			}
		} else {
			return forbidden();
		}
	}

	public static Result addRecords(String spaceId) {
		// TODO pass data with ajax (same as updating spaces of a single record)
		// can't pass parameter of type ObjectId, using String
		ObjectId sId = new ObjectId(spaceId);
		if (Secured.isOwnerOfSpace(sId)) {
			Map<String, String> data = Form.form().bindFromRequest().data();
			try {
				String recordsAdded = "";
				for (String recordId : data.keySet()) {
					// skip search input field
					if (recordId.equals("recordSearch")) {
						continue;
					}
					ObjectId rId = new ObjectId(recordId);
					String errorMessage = Space.addRecord(sId, rId);
					if (errorMessage != null) {
						// TODO remove previously added records?
						return badRequest(recordsAdded + errorMessage);
					}
					if (recordsAdded.isEmpty()) {
						recordsAdded = "Added some records, but then an error occurred: ";
					}
				}
				// TODO return ok();
				return redirect(routes.Spaces.show(spaceId));
			} catch (ConversionException e) {
				return internalServerError(e.getMessage());
			}
		} else {
			return forbidden();
		}
	}

	/**
	 * Return a list of records whose data contains the current search term and is not in the space already.
	 */
	public static Result searchRecords(String spaceIdString, String query) {
		List<Record> records = new ArrayList<Record>();
		int limit = 10;
		ObjectId userId = new ObjectId(request().username());
		Map<ObjectId, Set<ObjectId>> visibleRecords = User.getVisibleRecords(userId);
		ObjectId spaceId = new ObjectId(spaceIdString);
		Set<ObjectId> recordsAlreadyInSpace = Space.getRecords(spaceId);
		while (records.size() < limit) {
			// TODO use caching/incremental retrieval of results (scrolls)
			List<SearchResult> searchResults = Search.searchRecords(userId, visibleRecords, query);
			Set<ObjectId> recordIds = new HashSet<ObjectId>();
			for (SearchResult searchResult : searchResults) {
				recordIds.add(new ObjectId(searchResult.id));
			}
			recordIds.removeAll(recordsAlreadyInSpace);
			try {
				ObjectId[] targetArray = new ObjectId[recordIds.size()];
				records.addAll(Record.findAll(recordIds.toArray(targetArray)));
			} catch (ConversionException e) {
				return internalServerError(e.getMessage());
			}

			// TODO break if scrolling finds no more results
			break;
		}
		Collections.sort(records);
		return ok(recordsearchresults.render(records));
	}

	public static Result loadAllRecords() {
		try {
			List<Record> records = new ArrayList<Record>(Record.findVisible(new ObjectId(request().username())));
			Collections.sort(records);

			// format records
			List<ObjectNode> jsonRecords = new ArrayList<ObjectNode>(records.size());
			for (Record record : records) {
				ObjectNode jsonRecord = Json.newObject();
				jsonRecord.put("_id", record._id.toString());
				jsonRecord.put("creator", record.creator.toString());
				jsonRecord.put("owner", record.owner.toString());
				jsonRecord.put("created", record.created);
				jsonRecord.put("data", record.data);
				jsonRecord.put("description", record.description);
				jsonRecords.add(jsonRecord);
			}
			return ok(Json.toJson(jsonRecords));
		} catch (ConversionException e) {
			return badRequest(e.getMessage());
		}
	}

	public static Result loadRecords(String spaceId) {
		Set<ObjectId> records = Space.getRecords(new ObjectId(spaceId));
		List<String> recordIds = new ArrayList<String>(records.size());
		for (ObjectId recordId : records) {
			recordIds.add(recordId.toString());
		}
		return ok(Json.toJson(recordIds));
	}

	public static Result getVisualizationURL(String spaceId) {
		ObjectId visualizationId = Space.getVisualizationId(new ObjectId(spaceId), new ObjectId(request().username()));
		String url = Visualization.getURL(visualizationId);
		return ok(url);
	}

}
