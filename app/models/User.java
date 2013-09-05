package models;

import utils.ModelConversion;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.WriteResult;

import controllers.database.Connection;

public class User {

	private static final String collection = "users";

	public String email; // serves as id
	public String name;
	public String password;

	public static User find(String email) throws IllegalArgumentException, IllegalAccessException,
			InstantiationException {
		DBObject query = new BasicDBObject("email", email);
		DBObject result = Connection.getCollection(collection).findOne(query);
		if (result != null) {
			return ModelConversion.mapToModel(User.class, result.toMap());
		} else {
			return null;
		}
	}

	public static User authenticate(String email, String password) throws IllegalArgumentException,
			IllegalAccessException, InstantiationException {
		User user = find(email);
		if (user != null && user.password.equals(password)) {
			return user;
		} else {
			return null;
		}
	}

	public static String add(User newUser) throws IllegalArgumentException, IllegalAccessException {
		if (userWithSameEmailExists(newUser.email)) {
			return "A user with this email address already exists.";
		}
		DBObject insert = new BasicDBObject(ModelConversion.modelToMap(User.class, newUser));
		WriteResult result = Connection.getCollection(collection).insert(insert);
		return result.getLastError().getErrorMessage();
	}

	public static String remove(String email) {
		if (!userWithSameEmailExists(email)) {
			return "No user with this email address exists.";
		}
		DBObject query = new BasicDBObject("email", email);
		WriteResult result = Connection.getCollection(collection).remove(query);
		return result.getLastError().getErrorMessage();
	}

	private static boolean userWithSameEmailExists(String email) {
		DBObject query = new BasicDBObject("email", email);
		return (Connection.getCollection(collection).findOne(query) != null);
	}

	public static boolean isPerson(String email) {
		// TODO security check before casting to person?
		// requirement for record owners?
		return true;
	}

}
