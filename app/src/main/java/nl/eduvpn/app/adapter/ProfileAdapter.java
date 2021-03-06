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

package nl.eduvpn.app.adapter;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.squareup.picasso.Picasso;

import nl.eduvpn.app.R;
import nl.eduvpn.app.adapter.viewholder.ProfileViewHolder;
import nl.eduvpn.app.entity.Instance;
import nl.eduvpn.app.entity.Profile;
import nl.eduvpn.app.entity.SavedProfile;
import nl.eduvpn.app.service.HistoryService;
import nl.eduvpn.app.utils.FormattingUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter for the profile list.
 * Created by Daniel Zolnai on 2016-10-11.
 */
public class ProfileAdapter extends RecyclerView.Adapter<ProfileViewHolder> {

    private static final boolean UNDO_ENABLED = true;
    private static final int PENDING_REMOVAL_TIMEOUT = 5000;

    private List<Pair<Instance, Profile>> _profileList;
    private List<Pair<Instance, Profile>> _itemsPendingRemoval;
    private Map<Pair<Instance, Profile>, Runnable> _pendingRunnables = new HashMap<>();

    private Handler _handler = new Handler();

    private LayoutInflater _layoutInflater;
    private HistoryService _historyService;

    /**
     * Constructor.
     *
     * @param historyService The history service.
     * @param profileList    The list of instance and profile pairs to put in the list.
     */
    public ProfileAdapter(HistoryService historyService, @Nullable List<Pair<Instance, Profile>> profileList) {
        _historyService = historyService;
        _profileList = profileList;
        if (_profileList == null) {
            _profileList = new ArrayList<>();
        }
        _itemsPendingRemoval = new ArrayList<>();
    }

    /**
     * Adds new items to this adapter.
     *
     * @param profiles The list of profiles to add to the list of current items.
     */
    public synchronized void addItems(List<Pair<Instance, Profile>> profiles) {
        _profileList.addAll(profiles);
        notifyDataSetChanged();
    }

    /**
     * Returns the item at the given position.
     *
     * @param position The position of the item.
     * @return The item at the given position.
     */
    public Pair<Instance, Profile> getItem(int position) {
        return _profileList.get(position);
    }


    @Override
    public ProfileViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        if (_layoutInflater == null) {
            _layoutInflater = LayoutInflater.from(parent.getContext());
        }
        View view = _layoutInflater.inflate(R.layout.list_item_config, parent, false);
        return new ProfileViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ProfileViewHolder holder, int position) {
        final Pair<Instance, Profile> instanceProfilePair = getItem(position);
        if (_itemsPendingRemoval.contains(instanceProfilePair)) {
            // We need to show the "undo" state of the row
            Context context = holder.itemView.getContext();
            holder.itemView.setBackgroundColor(ContextCompat.getColor(context, R.color.swipe_background_color));
            holder.profileName.setVisibility(View.GONE);
            holder.providerIcon.setVisibility(View.GONE);
            holder.undoButton.setVisibility(View.VISIBLE);
            holder.undoButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // user wants to undo the removal, let's cancel the pending task
                    Runnable pendingRemovalRunnable = _pendingRunnables.get(instanceProfilePair);
                    _pendingRunnables.remove(instanceProfilePair);
                    if (pendingRemovalRunnable != null) {
                        _handler.removeCallbacks(pendingRemovalRunnable);
                    }
                    _itemsPendingRemoval.remove(instanceProfilePair);
                    // This will rebind the row in "normal" state
                    notifyItemChanged(_profileList.indexOf(instanceProfilePair));
                }

            });
        } else {
            holder.itemView.setBackgroundColor(Color.WHITE);
            holder.profileName.setVisibility(View.VISIBLE);
            holder.providerIcon.setVisibility(View.VISIBLE);
            holder.undoButton.setVisibility(View.GONE);
            holder.profileName.setText(instanceProfilePair.second.getDisplayName());
            holder.profileProvider.setText(FormattingUtils.formatInstanceUrl(instanceProfilePair.first));
            if (instanceProfilePair.first.getLogoUri() != null) {
                Picasso.with(holder.providerIcon.getContext())
                        .load(instanceProfilePair.first.getLogoUri())
                        .fit()
                        .noFade()
                        .into(holder.providerIcon);
            } else {
                holder.providerIcon.setImageResource(R.drawable.external_provider);
            }
        }
    }

    @Override
    public long getItemId(int position) {
        Pair<Instance, Profile> instanceProfilePair = getItem(position);
        return instanceProfilePair.first.getBaseURI().hashCode() +
                17 * instanceProfilePair.second.getProfileId().hashCode();
    }

    @Override
    public int getItemCount() {
        return _profileList != null ? _profileList.size() : 0;
    }


    /**
     * Returns if undoing is enabled.
     *
     * @return If undo is enabled.
     */
    public boolean isUndoEnabled() {
        return UNDO_ENABLED;
    }

    /**
     * Makes the removal of an item pending. The item will be removed soon unless the user presses on undo.
     *
     * @param position The position of the item.
     */
    public void pendingRemoval(int position) {
        final Pair<Instance, Profile> item = _profileList.get(position);
        if (!_itemsPendingRemoval.contains(item)) {
            _itemsPendingRemoval.add(item);
            // This will redraw row in "undo" state
            notifyItemChanged(position);
            // Let's create, store and post a runnable to remove the item
            Runnable pendingRemovalRunnable = new Runnable() {
                @Override
                public void run() {
                    remove(_profileList.indexOf(item));
                }
            };
            _handler.postDelayed(pendingRemovalRunnable, PENDING_REMOVAL_TIMEOUT);
            _pendingRunnables.put(item, pendingRemovalRunnable);
        }

    }

    /**
     * Removes the item at the given position.
     *
     * @param position The position of the item.
     */
    public void remove(int position) {
        Pair<Instance, Profile> item = _profileList.get(position);
        if (_itemsPendingRemoval.contains(item)) {
            _itemsPendingRemoval.remove(item);
        }
        if (_profileList.contains(item)) {
            _profileList.remove(position);
            _historyService.removeDiscoveredAPI(item.first.getSanitizedBaseURI());
            _historyService.removeSavedProfilesForInstance(item.first.getSanitizedBaseURI());
            _historyService.removeAccessTokens(item.first.getSanitizedBaseURI());
            notifyItemRemoved(position);
        }
    }

    /**
     * Returns if a given item is pending removal.
     *
     * @param position The position of the item.
     * @return True if removal is pending. Otherwise false.
     */
    public boolean isPendingRemoval(int position) {
        Pair<Instance, Profile> item = _profileList.get(position);
        return _itemsPendingRemoval.contains(item);
    }
}
