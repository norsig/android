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

package nl.eduvpn.app.entity;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

/**
 * A discovered API entity, containing all the URLs.
 * Created by Daniel Zolnai on 2016-10-18.
 */
public class DiscoveredAPI {

    private Integer _version;
    private String _authorizationEndpoint;
    private String _createConfigAPI;
    private String _profileListAPI;
    private String _systemMessagesAPI;
    private String _userMessagesAPI;

    public DiscoveredAPI(@NonNull Integer version,
                         @NonNull String authorizationEndpoint,
                         @NonNull String createConfigAPI,
                         @NonNull String profileListAPI,
                         @Nullable String systemMessagesAPI,
                         @Nullable String userMessagesAPI) {
        _version = version;
        _authorizationEndpoint = authorizationEndpoint;
        _createConfigAPI = createConfigAPI;
        _profileListAPI = profileListAPI;
        _systemMessagesAPI = systemMessagesAPI;
        _userMessagesAPI = userMessagesAPI;
    }

    public String getAuthorizationEndpoint() {
        return _authorizationEndpoint;
    }

    public Integer getVersion() {
        return _version;
    }

    public String getCreateConfigAPI() {
        return _createConfigAPI;
    }

    public String getProfileListAPI() {
        return _profileListAPI;
    }

    public String getSystemMessagesAPI() {
        return _systemMessagesAPI;
    }

    public String getUserMessagesAPI() {
        return _userMessagesAPI;
    }
}
