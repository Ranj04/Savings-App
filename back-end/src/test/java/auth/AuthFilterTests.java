package auth;

import applogic.CollectionTestTools;
import dto.AuthDto;
import handler.AuthFilter;
import org.apache.commons.codec.digest.DigestUtils;
import org.bson.Document;
import org.junit.Assert;
import org.mockito.Mockito;
import org.testng.annotations.Test;
import request.ParsedRequest;

import java.util.ArrayList;
import java.util.List;

public class AuthFilterTests {

    @Test(singleThreaded = true)
    public void testNotLoggedIn(){
        var testTools = new CollectionTestTools();
        Mockito.doReturn(new ArrayList()).when(testTools.authfindIterable).into(Mockito.any());

        var request = new ParsedRequest();
        String hash = DigestUtils.sha256Hex(String.valueOf(Math.random()));
        request.setCookieValue("auth", hash);
        AuthFilter.AuthResult result = AuthFilter.doFilter(request);
        Assert.assertFalse(result.isLoggedIn);
    }

    @Test(singleThreaded = true)
    public void loggedIn(){
        var testTools = new CollectionTestTools();
        var request = new ParsedRequest();
        String hash = DigestUtils.sha256Hex(String.valueOf(Math.random()));
        String userName = String.valueOf(Math.random());
        request.setCookieValue("auth", hash);

        var authEntry = new AuthDto();
        authEntry.setHash(hash);
        authEntry.setUserName(userName);
        authEntry.setExpireTime(java.time.Instant.now().getEpochSecond() + 86_400L);
        List<Document> returnList = new ArrayList();
        returnList.add(authEntry.toDocument());
        Mockito.doReturn(returnList).when(testTools.authfindIterable).into(Mockito.any());

        AuthFilter.AuthResult result = AuthFilter.doFilter(request);
        Assert.assertTrue(result.isLoggedIn);
        Assert.assertEquals(result.userName, userName);
    }
}
