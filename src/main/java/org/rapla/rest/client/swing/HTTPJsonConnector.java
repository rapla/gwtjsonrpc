package org.rapla.rest.client.swing;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Map;

@Deprecated
public class HTTPJsonConnector extends HTTPConnector
{
    public HTTPJsonConnector() {
        super();
    }

    public JsonObject sendPost(URL methodURL, JsonElement jsonObject) throws IOException,JsonParseException {
        return sendPost( methodURL, jsonObject, null);
    }

    public JsonObject sendPost(URL methodURL, JsonElement jsonObject, String authenticationToken) throws IOException,JsonParseException {
        return sendPost( methodURL, jsonObject, authenticationToken, Collections.emptyMap());
    }

    public JsonObject sendPost(URL methodURL, JsonElement jsonObject, String authenticationToken,Map<String, String>additionalHeaders) throws IOException,JsonParseException {
        return sendCall("POST", methodURL, jsonObject, authenticationToken, additionalHeaders);
    }

    public JsonObject sendGet(URL methodURL) throws IOException,JsonParseException  {
        return sendGet( methodURL, null);
    }

    public JsonObject sendGet(URL methodURL, String authenticationToken) throws IOException,JsonParseException  {
        return sendGet( methodURL, authenticationToken, Collections.emptyMap());
    }

    public JsonObject sendGet(URL methodURL, String authenticationToken, Map<String, String>additionalHeaders) throws IOException,JsonParseException  {
        return sendCall("GET", methodURL, null, authenticationToken, additionalHeaders);
    }

    public JsonObject sendPut(URL methodURL, JsonElement jsonObject, String authenticationToken) throws IOException,JsonParseException  {
        return sendPut( methodURL, jsonObject, authenticationToken, Collections.emptyMap());
    }

    public JsonObject sendPut(URL methodURL, JsonElement jsonObject, String authenticationToken, Map<String, String>additionalHeaders) throws IOException,JsonParseException  {
        return sendCall("PUT", methodURL, jsonObject, authenticationToken, additionalHeaders);
    }

    public JsonObject sendPatch(URL methodURL, JsonElement jsonObject) throws IOException
    {
        return sendPatch( methodURL,jsonObject,null, Collections.emptyMap());
    }
    public JsonObject sendPatch(URL methodURL, JsonElement jsonObject, String authenticationToken,Map<String, String>additionalHeaders) throws IOException,JsonParseException  {
        return sendCall("PATCH", methodURL, jsonObject, authenticationToken, additionalHeaders);
    }

    public JsonObject sendDelete(URL methodURL, String authenticationToken) throws IOException,JsonParseException  {
        return sendCall("DELETE", methodURL, null, authenticationToken, Collections.emptyMap());
    }

    public JsonObject sendDelete(URL methodURL, String authenticationToken,Map<String, String>additionalHeaders) throws IOException,JsonParseException  {
        return sendCall("DELETE", methodURL, null, authenticationToken, additionalHeaders);
    }

    protected JsonObject sendCall(String requestMethod, URL methodURL, JsonElement jsonObject, String authenticationToken,Map<String, String>additionalHeaders) throws  IOException,JsonParseException   {
        final String body = jsonToString(jsonObject);
        CallResult callResult = sendCallWithString(requestMethod, methodURL, body, authenticationToken, additionalHeaders);
        final int responseCode = callResult.getResponseCode();
        JsonObject response = new JsonObject();
        if ( responseCode == 200 )
        {
            final JsonElement parse = new JsonParser().parse(callResult.toString());
            response.add("result",parse );
        }
        else if ( responseCode != 204)
        {
            final JsonElement parse = new JsonParser().parse(callResult.toString());
            response.add("error",parse );
        }
        return response;
    }

    protected String jsonToString(JsonElement jsonObject)
    {
        final String body;
        if(jsonObject != null)
        {
            Gson gson = new GsonBuilder().disableHtmlEscaping().create();
            body = gson.toJson( jsonObject);

        }
        else
        {
            body = "";
        }
        return body;
    }

}