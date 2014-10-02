/*
 * Copyright (c) 2014, salesforce.com, inc.
 * All rights reserved.
 * Redistribution and use of this software in source and binary forms, with or
 * without modification, are permitted provided that the following conditions
 * are met:
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * - Neither the name of salesforce.com, inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission of salesforce.com, inc.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce.samples.smartsyncexplorer.ui;

import java.util.ArrayList;
import java.util.List;

import android.app.LoaderManager;
import android.content.Context;
import android.content.Loader;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.SearchView.OnCloseListener;
import android.widget.SearchView.OnQueryTextListener;
import android.widget.TextView;

import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.rest.RestClient;
import com.salesforce.androidsdk.smartsync.app.SmartSyncSDKManager;
import com.salesforce.androidsdk.smartsync.model.SalesforceObject;
import com.salesforce.androidsdk.smartsync.util.Constants;
import com.salesforce.androidsdk.ui.sfnative.SalesforceListActivity;
import com.salesforce.samples.smartsyncexplorer.R;
import com.salesforce.samples.smartsyncexplorer.loaders.MRUAsyncTaskLoader;

/**
 * Main activity.
 *
 * @author bhariharan
 */
public class MainActivity extends SalesforceListActivity implements
		OnQueryTextListener, OnCloseListener, LoaderManager.LoaderCallbacks<List<SalesforceObject>> {

	private static final int MRU_LOADER_ID = 1;

    private SearchView searchView;
    private MRUListAdapter listAdapter;
    private UserAccount curAccount;
	private NameFieldFilter nameFilter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		listAdapter = new MRUListAdapter(this, android.R.layout.simple_list_item_1);
		getListView().setAdapter(listAdapter);
		nameFilter = new NameFieldFilter(listAdapter);
	}

	@Override
	protected void refreshIfUserSwitched() {
		// TODO: User switch. Change 'client' and reload list. Also add logout functionality.
	}

	@Override
	public void onResume(RestClient client) {
		curAccount = SmartSyncSDKManager.getInstance().getUserAccountManager().getCurrentUser();
		getLoaderManager().initLoader(MRU_LOADER_ID, null, this).forceLoad();
    }

	@Override
	public void onPause() {
		getLoaderManager().destroyLoader(MRU_LOADER_ID);
		super.onPause();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
	    final MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.action_bar_menu, menu);
	    final MenuItem searchItem = menu.findItem(R.id.action_search);
	    searchView = new SalesforceSearchView(this);
        searchView.setOnQueryTextListener(this);
        searchView.setOnCloseListener(this);
        searchItem.setActionView(searchView);
	    return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    switch (item.getItemId()) {
	        case R.id.action_refresh:
	        	getLoaderManager().restartLoader(MRU_LOADER_ID, null, this);
	            return true;
	        default:
	            return super.onOptionsItemSelected(item);
	    }
	}

	@Override
	public boolean onQueryTextChange(String newText) {
		filterList(newText);
		return true;
    }

	@Override
	public Loader<List<SalesforceObject>> onCreateLoader(int id, Bundle args) {
		return new MRUAsyncTaskLoader(this, curAccount, Constants.USER);
	}

	@Override
	public void onLoaderReset(Loader<List<SalesforceObject>> loader) {
		refreshList(null);
	}

	@Override
	public void onLoadFinished(Loader<List<SalesforceObject>> loader,
			List<SalesforceObject> data) {
		refreshList(data);
	}

	@Override
	public boolean onClose() {
    	getLoaderManager().restartLoader(MRU_LOADER_ID, null, this);
		return true;
	}

	@Override
	public boolean onQueryTextSubmit(String query) {
		filterList(query);
		return true;
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		final SalesforceObject sObject = listAdapter.getItem(position);
		// TODO: Show detail screen.
	}

	private void refreshList(List<SalesforceObject> data) {
		listAdapter.setData(data);
	}

	private void filterList(String filterTerm) {
		if (TextUtils.isEmpty(filterTerm)) {
			return;
		}
		nameFilter.filter(filterTerm);
	}

	/**
	 * Custom search view that clears the search term when dismissed.
	 *
	 * @author bhariharan
	 */
	private static class SalesforceSearchView extends SearchView {

        public SalesforceSearchView(Context context) {
            super(context);
        }

        @Override
        public void onActionViewCollapsed() {
            setQuery("", false);
            super.onActionViewCollapsed();
        }
    }

	/**
	 * Custom array adapter to supply data to the list view.
	 *
	 * @author bhariharan
	 */
	private static class MRUListAdapter extends ArrayAdapter<SalesforceObject> {

		private int textViewId;
		private List<SalesforceObject> sObjects;

		/**
		 * Parameterized constructor.
		 *
		 * @param context Context.
		 * @param textViewResourceId Text view resource ID.
		 */
		public MRUListAdapter(Context context, int textViewResourceId) {
			super(context, textViewResourceId);
			textViewId = textViewResourceId;
		}

		/**
		 * Sets data to this adapter.
		 *
		 * @param data Data.
		 */
		public void setData(List<SalesforceObject> data) {
			clear();
			sObjects = data;
			if (data != null) {
				addAll(data);
				notifyDataSetChanged();
			}
		}

		@Override
		public View getView (int position, View convertView, ViewGroup parent) {
			if (convertView == null) {
				convertView = LayoutInflater.from(getContext()).inflate(textViewId,
						parent, false);
			}
			final TextView tv = (TextView) convertView;
			if (tv != null && sObjects != null) {
				tv.setText(sObjects.get(position).getName());
	            tv.setTextColor(Color.GREEN);
			}
			return tv;
		}
	}

	/**
	 * A simple utility class to implement filtering.
	 *
	 * @author bhariharan
	 */
	private static class NameFieldFilter extends Filter {

		private MRUListAdapter adpater;

		/**
		 * Parameterized constructor.
		 *
		 * @param adapter List adapter.
		 */
		public NameFieldFilter(MRUListAdapter adapter) {
			this.adpater = adapter;
		}

		@Override
		protected FilterResults performFiltering(CharSequence constraint) {
			if (TextUtils.isEmpty(constraint) || adpater == null) {
				return null;
			}
			final String filterString = constraint.toString().toLowerCase();
			final FilterResults results = new FilterResults();
			int count = adpater.getCount();
			String filterableString;
			final List<SalesforceObject> resultSet = new ArrayList<SalesforceObject>();
			for (int i = 0; i < count; i++) {
				filterableString = adpater.getItem(i).getName();
				if (filterableString.toLowerCase().contains(filterString)) {
					resultSet.add(adpater.getItem(i));
				}
			}
			results.values = resultSet;
			results.count = resultSet.size();
			return results;
		}

		@SuppressWarnings("unchecked")
		@Override
		protected void publishResults(CharSequence constraint, FilterResults results) {
			adpater.setData((List<SalesforceObject>) results.values);
		}
	}
}
