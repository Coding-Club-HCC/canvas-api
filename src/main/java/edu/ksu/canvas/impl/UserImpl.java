package edu.ksu.canvas.impl;

import com.google.gson.reflect.TypeToken;
import edu.ksu.canvas.constants.CanvasConstants;
import edu.ksu.canvas.exception.InvalidOauthTokenException;
import edu.ksu.canvas.interfaces.UserReader;
import edu.ksu.canvas.interfaces.UserWriter;
import edu.ksu.canvas.model.User;
import edu.ksu.canvas.net.Response;
import edu.ksu.canvas.net.RestClient;
import edu.ksu.canvas.oauth.OauthToken;
import edu.ksu.canvas.requestOptions.GetUsersInAccountOptions;
import edu.ksu.canvas.requestOptions.GetUsersInCourseOptions;
import edu.ksu.canvas.requestOptions.UpdateUserOptions;

import org.apache.log4j.Logger;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class UserImpl extends BaseImpl<User, UserReader, UserWriter> implements UserReader, UserWriter{
    private static final Logger LOG = Logger.getLogger(UserImpl.class);

    public UserImpl(String canvasBaseUrl, Integer apiVersion, OauthToken oauthToken, RestClient restClient,
                    int connectTimeout, int readTimeout, Integer paginationPageSize, Boolean serializeNulls) {
        super(canvasBaseUrl, apiVersion, oauthToken, restClient, connectTimeout, readTimeout,
                paginationPageSize, serializeNulls);
    }

    @Override
    public Optional<User> createUser(User user) throws InvalidOauthTokenException, IOException {
        String createdUrl = buildCanvasUrl("accounts/" + CanvasConstants.ACCOUNT_ID + "/users", Collections.emptyMap());
        LOG.debug("create URl for user creation : " + createdUrl);
        Response response = canvasMessenger.sendToCanvas(oauthToken, createdUrl, user.toPostMap(serializeNulls));
        if (response.getErrorHappened() || (response.getResponseCode() != 200)) {
            LOG.debug("Failed to create user, error message: " + response.toString());
            return Optional.empty();
        }
        return responseParser.parseToObject(User.class, response);
    }

    @Override
    public Optional<User> updateUser(User user) throws InvalidOauthTokenException, IOException {
        if(user == null || user.getId() == 0) {
            throw new IllegalArgumentException("User to update must not be null and have a Canvas ID assigned");
        }
        LOG.debug("updating user in Canvas: " + user.getId());
        String url = buildCanvasUrl("users/" + String.valueOf(user.getId()), Collections.emptyMap());

        //I tried to use the JSON POST method but Canvas throws odd permission errors when trying to serialize the user
        //object, even for an admin user. I suspect some field within the User object can't be updated and instead of
        //ignoring it like most calls, the user call throws a permission error. So I had to fall back to sending a form POST
        //since it only serializes fields with a @CanvasField annotation
        Response response = canvasMessenger.putToCanvas(oauthToken, url, user.toPostMap(serializeNulls));
        return responseParser.parseToObject(User.class, response);
    }

    @Override
    public List<User> getUsersInCourse(GetUsersInCourseOptions options) throws IOException {
        LOG.debug("Retrieving users in course " + options.getCourseId());
        String url = buildCanvasUrl("courses/" + options.getCourseId() + "/users", options.getOptionsMap());

        return getListFromCanvas(url);
    }

    @Override
    public Optional<User> showUserDetails(String userIdentifier) throws IOException {
        LOG.debug("Retrieving user details");
        String url = buildCanvasUrl("users/" + userIdentifier, Collections.emptyMap());
        Response response = canvasMessenger.getSingleResponseFromCanvas(oauthToken, url);
        return responseParser.parseToObject(User.class, response);
    }

    @Override
    public List<User> getAllUsers(GetUsersInAccountOptions options) throws IOException {
        LOG.debug("Retrieving users for account " + options.getAccountId());
        String url = buildCanvasUrl("accounts/" + options.getAccountId() + "/users", options.getOptionsMap());
        List<Response> response = canvasMessenger.getFromCanvas(oauthToken, url);
        return parseUserList(response);
    }

    @Override
    public Optional<User> updateUser(UpdateUserOptions options) throws InvalidOauthTokenException, IOException {
        LOG.debug("Updating basic user account information for Canvas user ID " + options.getInternalUserId());
        String url = buildCanvasUrl("users/" + options.getInternalUserId(), Collections.emptyMap());
        Response response = canvasMessenger.putToCanvas(oauthToken, url, options.getOptionsMap());
        return responseParser.parseToObject(User.class, response);
    }

    @Override
    protected Type listType() {
        return new TypeToken<List<User>>() {
        }.getType();
    }

    @Override
    protected Class<User> objectType() {
        return User.class;
    }

    private List<User> parseUserList(final List<Response> response) {
        return response.stream()
                       .map(this::parseUserResponse)
                       .flatMap(Collection::stream)
                       .collect(Collectors.toList());
    }

    private List<User> parseUserResponse(final Response response) {
        Type listType = new TypeToken<List<User>>() {
        }.getType();
        return GsonResponseParser.getDefaultGsonParser(true).fromJson(response.getContent(), listType);
    }
}
