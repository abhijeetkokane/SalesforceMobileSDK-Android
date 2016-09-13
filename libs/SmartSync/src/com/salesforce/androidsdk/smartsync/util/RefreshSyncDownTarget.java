/*
 * Copyright (c) 2016-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.smartsync.util;

import android.text.TextUtils;
import android.util.Log;

import com.salesforce.androidsdk.rest.RestRequest;
import com.salesforce.androidsdk.rest.RestResponse;
import com.salesforce.androidsdk.smartstore.store.QuerySpec;
import com.salesforce.androidsdk.smartsync.manager.SyncManager;
import com.salesforce.androidsdk.util.JSONObjectHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Target for sync which syncs down the records currently in a soup
 */
public class RefreshSyncDownTarget extends SyncDownTarget {

    private static final String TAG = "RefreshSyncDownTarget";
    public static final String FIELDLIST = "fieldlist";
    public static final String SOBJECT_TYPE = "sobjectType";
    public static final String SOUP_NAME = "soupName";
    private List<String> fieldlist;
    private String objectType;
    private String soupName;
    private int countIdsPerSoql = 500;
    private int page = 0;

    /**
     * Return number of ids to pack in a single SOQL call
     */
    public int getCountIdsPerSoql() {
        return countIdsPerSoql;
    }

    /**
     * Set the number of ids to pack in a single SOQL call
     * SOQL query size limit is 10,000 characters (so ~500 ids)
     * This setter is to be used by tests primarily
     * @param count
     */
    public void setCountIdsPerSoql(int count) {
        countIdsPerSoql = count;
    }

    /**
     * Construct RefreshSyncDownTarget from json
     * @param target
     * @throws JSONException
     */
    public RefreshSyncDownTarget(JSONObject target) throws JSONException {
        super(target);
        this.fieldlist = JSONObjectHelper.toList(target.getJSONArray(FIELDLIST));
        this.objectType = target.getString(SOBJECT_TYPE);
        this.soupName = target.getString(SOUP_NAME);
    }

    /**
     * Constructor
     * @param fieldlist
     * @param objectType
     */
    public RefreshSyncDownTarget(List<String> fieldlist, String objectType, String soupName) throws JSONException {
        super();
        this.queryType = QueryType.refresh;
        this.fieldlist = fieldlist;
        this.objectType = objectType;
        this.soupName = soupName;
    }

    /**
     * @return json representation of target
     * @throws JSONException
     */
    public JSONObject asJSON() throws JSONException {
        JSONObject target = super.asJSON();
        target.put(FIELDLIST, new JSONArray(fieldlist));
        target.put(SOBJECT_TYPE, objectType);
        target.put(SOUP_NAME, soupName);
        return target;
    }

    @Override
    public JSONArray startFetch(SyncManager syncManager, long maxTimeStamp) throws IOException, JSONException {
        return  getIdsFromSmartStoreAndFetchFromServer(syncManager);
        // NB refresh sync down are used for soups which are populated directly from the server by the app
        //    so reSync is not an option (individual records could have been saved at different time)
        //    and for that reason, we don't make use of maxTimeStamp
    }

    @Override
    public JSONArray continueFetch(SyncManager syncManager) throws IOException, JSONException {
        return page > 0 ? getIdsFromSmartStoreAndFetchFromServer(syncManager) : null;
    }

    private JSONArray getIdsFromSmartStoreAndFetchFromServer(SyncManager syncManager) throws IOException, JSONException {
        final List<String> idsInSmartStore = getIdsFromSmartStore(syncManager);
        final JSONArray records = fetchFromServer(syncManager, idsInSmartStore, fieldlist);

        final int countIdsFetched = getCountIdsPerSoql() * page + records.length();
        page = (countIdsFetched < totalSize ? page + 1 /* not done */: 0 /* done */);
        return records;
    }

    private List<String> getIdsFromSmartStore(SyncManager syncManager) throws JSONException {
        QuerySpec querySpec = QuerySpec.buildSmartQuerySpec("SELECT {" + soupName + ":" + getIdFieldName()
                + "} FROM {" + soupName + "}", getCountIdsPerSoql());

        if (page == 0) {
            totalSize = syncManager.getSmartStore().countQuery(querySpec);
        }

        JSONArray result = syncManager.getSmartStore().query(querySpec, page);
        List<String> ids = new ArrayList<>();
        for (int i=0; i<result.length(); i++) {
            ids.add(result.getJSONArray(i).getString(0));
        }
        return ids;
    }

    private JSONArray fetchFromServer(SyncManager syncManager, List<String> ids, List<String> fieldlist) throws IOException, JSONException {
        final String soql = SOQLBuilder.getInstanceWithFields(fieldlist).from(objectType).where(getIdFieldName()
                + " IN ('" + TextUtils.join("', '", ids) + "')").build();
        final RestRequest request = RestRequest.getRequestForQuery(syncManager.apiVersion, soql);
        final RestResponse response = syncManager.sendSyncWithSmartSyncUserAgent(request);
        JSONObject responseJson = response.asJSONObject();
        return responseJson.getJSONArray(Constants.RECORDS);
    }

    @Override
    public Set<String> getListOfRemoteIds(SyncManager syncManager, Set<String> localIds) {
        if (localIds == null) {
            return null;
        }

        Set<String> remoteIds = new HashSet<>();

        try {
            List<String> localIdsList = new ArrayList<>(localIds);
            int sliceSize = getCountIdsPerSoql();
            int countSlices = (int) Math.ceil((double) localIds.size() / sliceSize);
            for (int slice=0; slice<countSlices; slice++) {
                List<String> idsToFetch = localIdsList.subList(slice*sliceSize, Math.min(localIdsList.size(), (slice+1)*sliceSize));
                JSONArray records = fetchFromServer(syncManager, idsToFetch, Arrays.asList(getIdFieldName()));
                remoteIds.addAll(parseIdsFromResponse(records));
            }
        } catch (IOException e) {
            Log.e(TAG, "IOException thrown while fetching records", e);
        } catch (JSONException e) {
            Log.e(TAG, "JSONException thrown while fetching records", e);
        }

        return remoteIds;
    }

    /**
     * @return field list for this target
     */
    public List<String> getFieldlist() {
        return fieldlist;
    }

    /**
     * @return object type for this target
     */
    public String getObjectType() {
        return objectType;
    }
}
