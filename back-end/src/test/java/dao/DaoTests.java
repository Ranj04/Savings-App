package dao;

import applogic.CollectionTestTools;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.UpdateOptions;
import dto.AuthDto;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.bson.types.ObjectId;
import org.mockito.Mockito;
import org.testng.annotations.Test;

public class DaoTests {

  @Test
  public void testPut(){
    // checks that you call insertOne if this is a new item
    var test = new CollectionTestTools();
    AuthDao dao = AuthDao.getInstance();
    var item = new AuthDto();
    dao.put(item);
    Mockito.verify(test.mockAuthCollection).insertOne(Mockito.any());
  }

  @Test
  public void testPutExisting(){
    // checks that you call insertOne if this is a new item
    MongoCollection mongoCollection = Mockito.mock(MongoCollection.class);
    AuthDao dao = AuthDao.getInstance(mongoCollection);
    var item = new AuthDto();
    item.setUniqueId("6528b8c2c96ea939e01e1afa");
    dao.put(item);
    // Please use replace one to update existing objects
    Mockito.verify(mongoCollection).replaceOne(Mockito.any(), Mockito.any());
  }
}
