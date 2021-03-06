/*
 *  This file is part of eduVPN.
 *
 *     eduVPN is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     eduVPN is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with eduVPN.  If not, see <http://www.gnu.org/licenses/>.
 */

package nl.eduvpn.app.service;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.customtabs.CustomTabsClient;
import android.support.customtabs.CustomTabsIntent;
import android.support.v4.content.ContextCompat;

import nl.eduvpn.app.R;
import nl.eduvpn.app.entity.DiscoveredAPI;
import nl.eduvpn.app.entity.Instance;
import nl.eduvpn.app.utils.Log;

import java.util.UUID;

/**
 * The connection service takes care of building up the URLs and validating the result.
 * Created by Daniel Zolnai on 2016-10-11.
 */
public class ConnectionService {

    public class InvalidConnectionAttemptException extends Exception {
        public InvalidConnectionAttemptException(String message) {
            super(message);
        }
    }

    private static final String TAG = ConnectionService.class.getName();

    private static final String SCOPE = "config";
    private static final String RESPONSE_TYPE = "token";
    private static final String REDIRECT_URI = "nl.eduvpn.app://import/callback";
    private static final String CLIENT_ID = "nl.eduvpn.app";
    private static final String CUSTOM_TAB_PACKAGE_NAME = "com.android.chrome"; // This might change later,
    // as of now, this is the only browser which has Custom Tabs support.


    private PreferencesService _preferencesService;
    private HistoryService _historyService;
    private Context _context;
    private String _accessToken;

    /**
     * Constructor.
     *
     * @param context            The application or activity context.
     * @param preferencesService The preferences service used to store temporary data.
     */
    public ConnectionService(Context context, PreferencesService preferencesService, HistoryService historyService) {
        _context = context;
        _preferencesService = preferencesService;
        _historyService = historyService;
        _accessToken = preferencesService.getCurrentAccessToken();
    }

    /**
     * Builds up the connection URL from the base URL and the random state.
     *
     * @param authorizationEndpoint The base URL of the VPN provider.
     * @param state   The random state.
     * @return The connection URL which should be opened in the browser.
     */
    private String _buildConnectionUrl(@NonNull String authorizationEndpoint, @NonNull String state) {
        String sanitizedURL = authorizationEndpoint;
        if (sanitizedURL.endsWith("/")) {
            sanitizedURL = sanitizedURL.substring(0, sanitizedURL.length() - 1);
        }
        return sanitizedURL + "?client_id=" + CLIENT_ID +
                "&redirect_uri=" + REDIRECT_URI +
                "&response_type=" + RESPONSE_TYPE +
                "&scope=" + SCOPE +
                "&state=" + state;
    }

    /**
     * Generates a new random state.
     *
     * @return A new random state string.
     */
    private String _generateState() {
        return UUID.randomUUID().toString();
    }

    /**
     * Warms up the Custom Tabs service, allowing it to load even more faster.
     */
    public void warmup() {
        if (_preferencesService.getAppSettings().useCustomTabs()) {
            CustomTabsClient.connectAndInitialize(_context, CUSTOM_TAB_PACKAGE_NAME);
        }
    }

    /**
     * Initiates a connection to the VPN provider instance.
     *
     * @param activity      The current activity.
     * @param instance      The instance to connect to.
     * @param discoveredAPI The discovered API which has the URL.
     */
    public void initiateConnection(@NonNull Activity activity, @NonNull Instance instance, @NonNull DiscoveredAPI discoveredAPI) {
        String authorizationUrl = discoveredAPI.getAuthorizationEndpoint();
        String state = _generateState();
        String connectionUrl = _buildConnectionUrl(authorizationUrl, state);

        _preferencesService.currentConnectionState(state);
        _preferencesService.currentInstance(instance);
        _preferencesService.currentDiscoveredAPI(discoveredAPI);
        if (!_preferencesService.getAppSettings().useCustomTabs()) {
            Intent viewUrlIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(connectionUrl));
            activity.startActivity(viewUrlIntent);
        } else {
            CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
            builder.setToolbarColor(ContextCompat.getColor(_context, R.color.mainColor));
            builder.setInstantAppsEnabled(false);
            builder.setShowTitle(true);
            CustomTabsIntent customTabsIntent = builder.build();
            customTabsIntent.launchUrl(activity, Uri.parse(connectionUrl));
        }
    }

    /**
     * Checks if the returned state is valid.
     *
     * @param state The returned state.
     * @return True if the state is valid and matches the saved state. Else false.
     */
    private boolean _validateState(String state) {
        String savedState = _preferencesService.getCurrentConnectionState();
        return state != null && savedState != null && savedState.equals(state);
    }

    /**
     * Parses the callback intent and retrieves the access token.
     *
     * @param intent The intent to parse.
     * @throws InvalidConnectionAttemptException Thrown if there's a problem with the callback.
     */
    public void parseCallbackIntent(@NonNull Intent intent) throws InvalidConnectionAttemptException {
        Uri callbackUri = intent.getData();
        if (callbackUri == null) {
            // Not a callback intent. Check before calling this method.
            throw new RuntimeException("Intent is not a callback intent!");
        }
        Log.i(TAG, "Got callback URL: " + callbackUri.toString());
        // Modify it so the URI parser can parse the params.
        callbackUri = Uri.parse(callbackUri.toString().replace("callback#", "callback?"));

        String accessToken = callbackUri.getQueryParameter("access_token");
        String state = callbackUri.getQueryParameter("state");
        String error = callbackUri.getQueryParameter("error");
        if (error != null) {
            if ("access_denied".equals(error)) {
                throw new InvalidConnectionAttemptException(_context.getString(R.string.rejected_permission));
            } else {
                throw new InvalidConnectionAttemptException(_context.getString(R.string.callback_error, error));
            }
        }
        if (accessToken == null || accessToken.isEmpty()) {
            throw new InvalidConnectionAttemptException(_context.getString(R.string.error_access_token_missing));
        }
        if (state == null || state.isEmpty()) {
            throw new InvalidConnectionAttemptException(_context.getString(R.string.error_state_missing));
        }
        // Make sure the state is valid
        boolean isStateValid = _validateState(state);

        if (!isStateValid) {
            throw new InvalidConnectionAttemptException(_context.getString(R.string.error_state_mismatch));
        }
        // Save the access token
        _accessToken = accessToken;
        _preferencesService.currentAccessToken(accessToken);
        // Now we can delete the saved state
        _preferencesService.removeCurrentConnectionState();
        // Save the access token for later use.
        _historyService.cacheAccessToken(_preferencesService.getCurrentInstance(), _accessToken);
    }

    /**
     * Returns the access token which authenticates against the current API.
     *
     * @return The access token used to authenticate.
     */
    public String getAccessToken() {
        return _accessToken;
    }

    /**
     * Sets and saved the current access token to use with the requests.
     *
     * @param accessToken The access token to use for getting resources.
     */
    public void setAccessToken(String accessToken) {
        _accessToken = accessToken;
        _preferencesService.currentAccessToken(accessToken);
    }
}
