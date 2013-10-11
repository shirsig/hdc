package models;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.bson.types.ObjectId;

import utils.Connection;
import utils.ModelConversion;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.WriteResult;

public class Visualization extends SearchableModel {

	private static final String collection = "visualizations";

	public String name;
	public String description;
	public String url;

	public static String getName(ObjectId visualizationId) {
		DBObject query = new BasicDBObject("_id", visualizationId);
		DBObject projection = new BasicDBObject("name", 1);
		return (String) Connection.getCollection(collection).findOne(query, projection).get("name");
	}
	
	public static String getURL(ObjectId visualizationId) {
		DBObject query = new BasicDBObject("_id", visualizationId);
		DBObject projection = new BasicDBObject("url", 1);
		return (String) Connection.getCollection(collection).findOne(query, projection).get("url");
	}

	public static Visualization find(ObjectId visualizationId) throws IllegalArgumentException, IllegalAccessException,
			InstantiationException {
		DBObject query = new BasicDBObject("_id", visualizationId);
		DBObject result = Connection.getCollection(collection).findOne(query);
		return ModelConversion.mapToModel(Visualization.class, result.toMap());
	}

	public static List<Visualization> findInstalledBy(ObjectId userId) throws IllegalArgumentException,
			IllegalAccessException, InstantiationException {
		Set<ObjectId> visualizationIds = Installed.findVisualizationsInstalledBy(userId);
		List<Visualization> visualizations = new ArrayList<Visualization>();
		for (ObjectId visualizationId : visualizationIds) {
			visualizations.add(find(visualizationId));
		}
		// TODO sort
		return visualizations;
	}

	public static String add(Visualization newVisualization) throws IllegalArgumentException, IllegalAccessException {
		if (visualizationWithSameNameExists(newVisualization.name)) {
			return "A visualization with this name already exists.";
		}
		DBObject insert = new BasicDBObject(ModelConversion.modelToMap(newVisualization));
		WriteResult result = Connection.getCollection(collection).insert(insert);
		newVisualization._id = (ObjectId) insert.get("_id");
		return result.getLastError().getErrorMessage();
	}

	public static String delete(ObjectId visualizationId) {
		if (!visualizationExists(visualizationId)) {
			return "No visualizations with this id exists.";
		}

		// remove from installed
		Installed.deleteVisualization(visualizationId);

		// remove from visualizations
		DBObject remove = new BasicDBObject("_id", visualizationId);
		WriteResult result = Connection.getCollection(collection).remove(remove);
		return result.getLastError().getErrorMessage();
	}

	private static boolean visualizationExists(ObjectId visualizationId) {
		DBObject query = new BasicDBObject("_id", visualizationId);
		return Connection.getCollection(collection).findOne(query) != null;
	}

	private static boolean visualizationWithSameNameExists(String name) {
		DBObject query = new BasicDBObject("name", name);
		return Connection.getCollection(collection).findOne(query) != null;
	}

}
