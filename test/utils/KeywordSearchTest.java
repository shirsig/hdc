package utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static play.test.Helpers.fakeApplication;
import static play.test.Helpers.fakeGlobal;
import static play.test.Helpers.start;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.List;

import models.Record;

import org.bson.types.ObjectId;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;

public class KeywordSearchTest {

	private final String[] keywordList = { "doctor", "dentist", "runtastic", "x-ray", "image" };

	@Before
	public void setUp() throws Exception {
		start(fakeApplication(fakeGlobal()));
		TestConnection.connectToTest();
		TestConnection.dropDatabase();
		insertRecordKeywords();
	}

	@After
	public void tearDown() {
		TestConnection.close();
	}

	@Test
	public void singleMatch() throws Exception {
		List<Record> list = KeywordSearch.searchByType(Record.class, Record.getCollection(), keywordList[1], 10);
		assertEquals(1, list.size());
		assertTrue(list.get(0).tags.contains(keywordList[1]));
	}

	@Test
	public void multiMatch() throws Exception {
		String email = (String) TestConnection.getCollection("users").findOne().get("email");
		ObjectId[] recordIds = CreateDBObjects.insertRecords(email, email, 2);
		DBCollection collection = TestConnection.getCollection("records");
		collection.update(new BasicDBObject("_id", recordIds[0]), new BasicDBObject("$push", new BasicDBObject("tags",
				new BasicDBObject("$each", new String[] { keywordList[1], keywordList[3] }))));
		collection.update(new BasicDBObject("_id", recordIds[1]), new BasicDBObject("$push", new BasicDBObject("tags",
				new BasicDBObject("$each", new String[] { keywordList[1], keywordList[3] }))));
		List<Record> list = KeywordSearch.searchByType(Record.class, Record.getCollection(), keywordList[1] + " "
				+ keywordList[3], 10);
		assertEquals(2, list.size());
		for (Record record : list) {
			assertTrue(keywordsInTags(record, keywordList[1], keywordList[3]));
		}
	}

	@Test
	public void multiNoMatch() throws Exception {
		List<Record> list = KeywordSearch.searchByType(Record.class, Record.getCollection(), keywordList[1] + " "
				+ keywordList[3] + " " + keywordList[4], 10);
		assertEquals(0, list.size());

	}

	@Test
	public void noMatch() throws Exception {
		List<Record> list = KeywordSearch.searchByType(Record.class, Record.getCollection(), "none", 10);
		assertEquals(0, list.size());
	}

	private boolean keywordsInTags(Record record, String... keywords) {
		boolean found = false;
		for (String keyword : keywords) {
			if (record.tags.contains(keyword)) {
				found = true;
				break;
			}
		}
		return found;
	}

	private void insertRecordKeywords() throws IllegalArgumentException, IllegalAccessException,
			NoSuchAlgorithmException, InvalidKeySpecException {
		String[] emails = CreateDBObjects.insertUsers(2);
		ObjectId[] recordIds = CreateDBObjects.insertRecords(emails[1], emails[0], keywordList.length);
		DBCollection collection = TestConnection.getCollection("records");
		for (int i = 0; i < keywordList.length; i++) {
			collection.update(new BasicDBObject("_id", recordIds[i]), new BasicDBObject("$push", new BasicDBObject(
					"tags", keywordList[i])));
		}
	}

}