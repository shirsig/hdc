package controllers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import models.Record;
import models.Space;

import org.bson.types.ObjectId;
import org.codehaus.jackson.node.ObjectNode;

import play.data.Form;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Security;
import utils.DateTimeUtils;
import utils.KeywordSearch;
import views.html.spaces;
import views.html.elements.spaces.recordForm;

import com.mongodb.BasicDBList;

import controllers.forms.SpaceForm;

@Security.Authenticated(Secured.class)
public class Spaces extends Controller {

	public static Result show(String activeSpaceId) {
		try {
			String user = request().username();
			ObjectId activeSpace = null;
			if (activeSpaceId != null) {
				activeSpace = new ObjectId(activeSpaceId);
			}
			return ok(spaces.render(Form.form(SpaceForm.class), Record.findSharedWith(user), Space.findOwnedBy(user),
					activeSpace, user));
		} catch (IllegalArgumentException e) {
			return internalServerError(e.getMessage());
		} catch (IllegalAccessException e) {
			return internalServerError(e.getMessage());
		} catch (InstantiationException e) {
			return internalServerError(e.getMessage());
		}
	}

	/**
	 * Validation helper for space form (we only have access to current user in controllers).
	 */
	public static String validateSpace(String name, String visualization) {
		Space newSpace = new Space();
		newSpace.name = name;
		newSpace.owner = request().username();
		newSpace.visualization = visualization;
		newSpace.records = new BasicDBList();
		try {
			String errorMessage = Space.add(newSpace);
			if (errorMessage != null) {
				return errorMessage;
			} else {
				// pass id of space back in case of success
				return "ObjectId:" + newSpace._id.toString();
			}
			// multi-catch doesn't seem to work...
		} catch (IllegalArgumentException e) {
			return e.getMessage();
		} catch (IllegalAccessException e) {
			return e.getMessage();
		}
	}

	public static Result add() {
		Form<SpaceForm> spaceForm = Form.form(SpaceForm.class).bindFromRequest();
		if (spaceForm.hasErrors()) {
			try {
				String user = request().username();
				return badRequest(spaces.render(spaceForm, Record.findSharedWith(user), Space.findOwnedBy(user), null,
						user));
			} catch (IllegalArgumentException e) {
				return internalServerError(e.getMessage());
			} catch (IllegalAccessException e) {
				return internalServerError(e.getMessage());
			} catch (InstantiationException e) {
				return internalServerError(e.getMessage());
			}
		} else {
			// TODO (?) js ajax insertion, open newly added space
			// return ok(space.render(newSpace));
			return redirect(routes.Spaces.show(spaceForm.get().spaceId));
		}
	}

	public static Result rename(String spaceId) {
		// can't pass parameter of type ObjectId, using String
		ObjectId id = new ObjectId(spaceId);
		if (Secured.isOwnerOfSpace(id)) {
			String newName = Form.form().bindFromRequest().get("name");
			String errorMessage = Space.rename(id, newName);
			if (errorMessage == null) {
				return ok(newName);
			} else {
				return badRequest(errorMessage);
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
				return redirect(routes.Spaces.show(spaceId));
			} catch (IllegalArgumentException | IllegalAccessException | InstantiationException e) {
				return internalServerError(e.getMessage());
			}
		} else {
			return forbidden();
		}
	}

	public static Result removeRecord(String spaceId) {
		// can't pass parameter of type ObjectId, using String
		ObjectId sId = new ObjectId(spaceId);
		if (Secured.isOwnerOfSpace(sId)) {
			String recordId = Form.form().bindFromRequest().get("id");
			ObjectId rId = new ObjectId(recordId);
			try {
				String errorMessage = Space.removeRecord(sId, rId);
				if (errorMessage == null) {
					return ok();
				} else {
					return badRequest(errorMessage);
				}
			} catch (IllegalArgumentException | IllegalAccessException | InstantiationException e) {
				return internalServerError(e.getMessage());
			}
		} else {
			return forbidden();
		}
	}

	public static Result updateRecords(String recordId, List<String> spaces) {
		List<ObjectId> spaceIds = new ArrayList<ObjectId>();
		for (String id : spaces) {
			spaceIds.add(new ObjectId(id));
		}
		try {
			String errorMessage = Space.updateRecords(spaceIds, new ObjectId(recordId), request().username());
			if (errorMessage == null) {
				return ok();
			} else {
				return badRequest(errorMessage);
			}
		} catch (IllegalArgumentException e) {
			return internalServerError(e.getMessage());
		} catch (IllegalAccessException e) {
			return internalServerError(e.getMessage());
		} catch (InstantiationException e) {
			return internalServerError(e.getMessage());
		}
	}

	public static Result manuallyAddRecord() {
		Record newRecord = new Record();
		newRecord.creator = request().username();
		newRecord.owner = newRecord.creator;
		newRecord.created = DateTimeUtils.getNow();
		newRecord.data = Form.form().bindFromRequest().get("data");
		newRecord.tags = new BasicDBList();
		for (String tag : Form.form().bindFromRequest().get("tags").toLowerCase().split("[ ,\\+]+")) {
			newRecord.tags.add(tag);
		}
		try {
			String errorMessage = Record.add(newRecord);
			if (errorMessage == null) {
				return redirect(routes.Application.spaces());
			} else {
				return badRequest(errorMessage);
			}
		} catch (IllegalArgumentException e) {
			return internalServerError(e.getMessage());
		} catch (IllegalAccessException e) {
			return internalServerError(e.getMessage());
		}
	}

	/**
	 * Return a list of records whose data contains the current search term and is not in the space already.
	 */
	public static Result searchRecords(String spaceId, String search) {
		List<Record> response = new ArrayList<Record>();
		try {
			// TODO use caching
			String user = request().username();
			ObjectId sId = new ObjectId(spaceId);
			if (search == null || search.isEmpty()) {
				response = Record.findSharedWith(user);
			} else {
				response = KeywordSearch.searchByType(Record.class, Record.getCollection(), search, 10);
			}
			response = Space.makeDisjoint(sId, response);
			return ok(recordForm.render(response));
		} catch (IllegalArgumentException e) {
			return badRequest(e.getMessage());
		} catch (IllegalAccessException e) {
			return badRequest(e.getMessage());
		} catch (InstantiationException e) {
			return badRequest(e.getMessage());
		}
	}

	/**
	 * Find the spaces that contain the given record.
	 */
	public static Result findSpacesWith(String recordId) {
		Set<ObjectId> spaceIds = Space.findWithRecord(new ObjectId(recordId), request().username());
		Set<String> spaces = new HashSet<String>();
		for (ObjectId id : spaceIds) {
			spaces.add(id.toString());
		}
		return ok(Json.toJson(spaces));
	}

	public static Set<ObjectId> findCirclesWith(String recordId) {
		return null;
	}

	public static Result loadSpace() {
		List<Record> records = null;
		try {
			records = Record.findSharedWith(request().username());
		} catch (IllegalArgumentException e) {
			return badRequest(e.getMessage());
		} catch (IllegalAccessException e) {
			return badRequest(e.getMessage());
		} catch (InstantiationException e) {
			return badRequest(e.getMessage());
		}
		ObjectNode request = Json.newObject();
		ObjectNode recs = Json.newObject();
		for (Record record : records) {
			recs.put(record._id + " created", record.created);
			recs.put(record._id + " data", record.data);
		}
		request.put("records", recs);
		return ok(request);
	}
}
