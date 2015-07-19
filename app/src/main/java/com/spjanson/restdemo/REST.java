package com.spjanson.restdemo;
/**
 * Copyright 2015 Sean Janson. All Rights Reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.os.AsyncTask;

import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.common.AccountPicker;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.ParentReference;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

final class REST { private REST() {}
  interface ConnectCBs {
    void onConnFail(Intent it);
    void onConnOK();
  }
  private static Drive mGOOSvc;
  private static ConnectCBs mConnCBs;
  private static boolean mConnected;

  /************************************************************************************************
   * initialize Google Drive Api
   * @param act   activity context
   */
  static boolean init(Activity act){                    //UT.lg( "REST init " + email);
    if (act != null) try {
      String email = UT.AM.getEmail();
      if (email != null) {
        mConnCBs = (ConnectCBs)act;
        mGOOSvc = new Drive.Builder(AndroidHttp.newCompatibleTransport(), new GsonFactory(),
        GoogleAccountCredential.usingOAuth2(UT.acx, Collections.singletonList(DriveScopes.DRIVE_FILE))
        .setSelectedAccountName(email)
        ).build();
        return true;
      }
    } catch (Exception e) {UT.le(e);}
    return false;
  }
  /**
   * connect
   */
  static void connect() {
    if (UT.AM.getEmail() != null && mGOOSvc != null) {
      mConnected = false;
      new AsyncTask<Void, Void, UserRecoverableAuthIOException>(){
        @Override
        protected UserRecoverableAuthIOException doInBackground(Void... nadas) {
          try {
            mGOOSvc.files().get("root").setFields("title").execute();
            mConnected = true;
          }
          catch (UserRecoverableAuthIOException uraIOEx) {
            return uraIOEx;
          }
          catch (IOException e) {   // '404 not found' in FILE scope, consider connected
            if (e instanceof GoogleJsonResponseException) {
              if (404 == ((GoogleJsonResponseException)e).getStatusCode())
                mConnected = true;
            }
          } catch (Exception e) { UT.le(e); }
          return null;
        }
        @Override
        protected void onPostExecute(UserRecoverableAuthIOException ex) {
          super.onPostExecute(ex);
          if (mConnected) {
            mConnCBs.onConnOK();
          } else {  // null indictes general error (fatal)
            mConnCBs.onConnFail(ex == null ? null : ex.getIntent());
          }
        }
      }.execute();
    }
  }
  /**
   * disconnect    disconnects GoogleApiClient
   */
  static void disconnect() {}

  /************************************************************************************************
   * find file/folder in GOODrive
   * @param prnId   parent ID (optional), null searches full drive, "root" searches Drive root
   * @param titl    file/folder name (optional)
   * @param mime    file/folder mime type (optional)
   * @return        arraylist of found objects
   */
  static ArrayList<ContentValues> search(String prnId, String titl, String mime) {
    ArrayList<ContentValues> gfs = new ArrayList<>();
    if (mGOOSvc != null && mConnected) try {
      // add query conditions, build query
      String qryClause = "'me' in owners and ";
      if (prnId != null) qryClause += "'" + prnId + "' in parents and ";
      if (titl != null) qryClause += "title = '" + titl + "' and ";
      if (mime != null) qryClause += "mimeType = '" + mime + "' and ";
      qryClause = qryClause.substring(0, qryClause.length() - " and ".length());
      Drive.Files.List qry = mGOOSvc.files().list().setQ(qryClause)
      .setFields("items(id,mimeType,labels/trashed,title),nextPageToken");
      String npTok = null;
      if (qry != null) do {
        FileList gLst = qry.execute();
        if (gLst != null) {
          for (File gFl : gLst.getItems()) {
            if (gFl.getLabels().getTrashed()) continue;
            gfs.add( UT.newCVs(gFl.getTitle(),gFl.getId()));
          }                                                                 //else UT.lg("failed " + gFl.getTitle());
          npTok = gLst.getNextPageToken();
          qry.setPageToken(npTok);
        }
      } while (npTok != null && npTok.length() > 0);                     //UT.lg("found " + vlss.size());
    } catch (Exception e) { UT.le(e); }
    return gfs;
  }

  /************************************************************************************************
   * create file/folder in GOODrive
   * @param prnId  parent's ID, (null or "root") for root
   * @param titl  file name
   * @return      file id  / null on fail
   */
  static String createFolder(String prnId, String titl) {
    String rsId = null;
    if (mGOOSvc != null && mConnected && titl != null) try {
      File meta = new File();
      meta.setParents(Arrays.asList(new ParentReference().setId(prnId == null ? "root" : prnId)));
      meta.setTitle(titl);
      meta.setMimeType(UT.MIME_FLDR);

      File gFl = null;
      try { gFl = mGOOSvc.files().insert(meta).execute();
      } catch (Exception e) { UT.le(e); }
      if (gFl != null && gFl.getId() != null) {
        rsId = gFl.getId();
      }
    } catch (Exception e) { UT.le(e); }
    return rsId;
  }

  /************************************************************************************************
   * create file/folder in GOODrive
   * @param prnId  parent's ID, (null or "root") for root
   * @param titl  file name
   * @param mime  file mime type
   * @param file  file (with content) to create
   * @return      file id  / null on fail
   */
  static String createFile(String prnId, String titl, String mime, java.io.File file) {
    String rsId = null;
    if (mGOOSvc != null && mConnected && titl != null && mime != null && file != null) try {
      File meta = new File();
      meta.setParents(Arrays.asList(new ParentReference().setId(prnId == null ? "root" : prnId)));
      meta.setTitle(titl);
      meta.setMimeType(mime);

      File gFl = mGOOSvc.files().insert(meta, new FileContent(mime, file)).execute();
      if (gFl != null)
        rsId = gFl.getId();
    } catch (Exception e) { UT.le(e); }
    return rsId;
  }

  /************************************************************************************************
   * get file contents
   * @param resId  file driveId
   * @return       file's content  / null on fail
   */
  static byte[] read(String resId) {
    if (mGOOSvc != null && mConnected && resId != null) try {
      File gFl = mGOOSvc.files().get(resId).setFields("downloadUrl").execute();
      if (gFl != null){
        String strUrl = gFl.getDownloadUrl();
        return UT.is2Bytes(mGOOSvc.getRequestFactory().buildGetRequest(new GenericUrl(strUrl)).execute().getContent());
      }
    } catch (Exception e) { UT.le(e); }
    return null;
  }

  /************************************************************************************************
   * update file in GOODrive,  see https://youtu.be/r2dr8_Mxr2M (WRONG?)
   * see https://youtu.be/r2dr8_Mxr2M   .... WRONG !!!
   * @param resId  file  id
   * @param titl  new file name (optional)
   * @param mime  new mime type (optional, "application/vnd.google-apps.folder" indicates folder)
   * @param file  new file content (optional)
   * @return      file id  / null on fail
   */
  static String update(String resId, String titl, String mime, String desc, java.io.File file){
    File gFl = null;
    if (mGOOSvc != null && mConnected && resId != null) try {
      File meta = new File();
      if (titl != null) meta.setTitle(titl);
      if (mime != null) meta.setMimeType(mime);
      if (desc != null) meta.setDescription(desc);

      if (file == null)
        gFl = mGOOSvc.files().patch(resId, meta).execute();
      else
        gFl = mGOOSvc.files().update(resId, meta, new FileContent(mime, file)).execute();

    } catch (Exception e) { UT.le(e); }
    return gFl == null ? null : gFl.getId();
  }

  /************************************************************************************************
   * trash file in GOODrive
   * @param resId  file  id
   * @return       success status
   */
  static boolean trash(String resId) {
    if (mGOOSvc != null && mConnected && resId != null) try {
      return null != mGOOSvc.files().trash(resId).execute();
    } catch (Exception e) {UT.le(e);}
    return false;
  }

  /**
   * FILE / FOLDER type object inquiry
   * @param gdId Drive ID
   * @return TRUE if FOLDER, FALSE otherwise
   */
  static boolean isFolder(String gdId) {
    if (mGOOSvc != null && mConnected && gdId != null) try {
      String mime = (mGOOSvc.files().get(gdId).setFields("mimeType").execute().getMimeType());
      return (UT.MIME_FLDR.equalsIgnoreCase(mime));
    } catch (Exception e) {UT.le(e);}
    return false;
  }

}

