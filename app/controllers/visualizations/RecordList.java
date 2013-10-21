package controllers.visualizations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import models.Circle;
import models.Record;
import models.Space;

import org.bson.types.ObjectId;

import play.cache.Cache;
import play.libs.Json;
import play.mvc.BodyParser;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Security;
import views.html.visualizations.list;

import com.fasterxml.jackson.databind.JsonNode;

import controllers.Secured;

@Security.Authenticated(Secured.class)
public class RecordList extends Controller {

	@BodyParser.Of(BodyParser.Json.class)
	public static Result load() {
		// check whether the request is complete
		JsonNode json = request().body().asJson();
		String requestComplete = Visualizations.requestComplete(json);
		if (requestComplete != null) {
			return badRequest(requestComplete);
		}

		// parse the space id and the records
		String spaceId = json.get("spaceId").asText();
		List<Record> records = new ArrayList<Record>();
		for (JsonNode cur : json.get("records")) {
			Record newRecord = new Record();
			Iterator<Entry<String, JsonNode>> fields = cur.fields();
			while (fields.hasNext()) {
				Entry<String, JsonNode> curField = fields.next();
				try {
					if (Record.class.getField(curField.getKey()).getType().equals(ObjectId.class)) {
						Record.class.getField(curField.getKey()).set(newRecord,
								new ObjectId(curField.getValue().asText()));
					} else {
						Record.class.getField(curField.getKey()).set(newRecord, curField.getValue().asText());
					}
				} catch (IllegalArgumentException e) {
					return internalServerError(e.getMessage());
				} catch (IllegalAccessException e) {
					return internalServerError(e.getMessage());
				} catch (NoSuchFieldException e) {
					return internalServerError(e.getMessage());
				} catch (SecurityException e) {
					return internalServerError(e.getMessage());
				}
			}
			records.add(newRecord);
		}

		// sort the records and create the response
		Collections.sort(records);
		Result response = ok(list.render(spaceId, records, new ObjectId(request().username())));

		// cache the response and return the url to retrieve it (will be loaded in iframe)
		ObjectId requestId = new ObjectId();
		Cache.set(requestId.toString() + ":" + request().username(), response);
		return ok(routes.Visualizations.show(requestId.toString()).url());
	}

	/**
	 * Find the spaces that contain the given record.
	 */
	public static Result findSpacesWith(String recordId) {
		Set<ObjectId> spaceIds = Space.findWithRecord(new ObjectId(recordId), new ObjectId(request().username()));
		Set<String> spaces = new HashSet<String>();
		for (ObjectId id : spaceIds) {
			spaces.add(id.toString());
		}
		return ok(Json.toJson(spaces));
	}

	/**
	 * Find the circles the given record is shared with.
	 */
	public static Result findCirclesWith(String recordId) {
		Set<ObjectId> circleIds = Circle.findWithRecord(new ObjectId(recordId), new ObjectId(request().username()));
		Set<String> circles = new HashSet<String>();
		for (ObjectId id : circleIds) {
			circles.add(id.toString());
		}
		return ok(Json.toJson(circles));
	}

	/**
	 * Updates the spaces the given record is in.
	 */
	public static Result updateSpaces(String recordId, List<String> spaces) {
		List<ObjectId> spaceIds = new ArrayList<ObjectId>();
		for (String id : spaces) {
			spaceIds.add(new ObjectId(id));
		}
		try {
			String errorMessage = Space.updateRecords(spaceIds, new ObjectId(recordId), new ObjectId(request()
					.username()));
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

	/**
	 * Updates the circles the given record is shared with.
	 */
	public static Result updateCircles(String record, List<String> circles) {
		ObjectId recordId = new ObjectId(record);
		List<ObjectId> circleIds = new ArrayList<ObjectId>();
		for (String id : circles) {
			circleIds.add(new ObjectId(id));
		}

		// TODO Security checks here?
		if (!Secured.isCreatorOrOwnerOfRecord(recordId)) {
			return forbidden();
		}
		for (ObjectId circleId : circleIds) {
			if (!Secured.isOwnerOfCircle(circleId)) {
				return forbidden();
			}
		}

		// update circles
		try {
			String errorMessage = Circle.updateShared(circleIds, recordId, new ObjectId(request().username()));
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

}
