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

import android.accounts.AccountManager;
import android.content.ContentValues;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.common.AccountPicker;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAuthIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;

import java.io.File;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements REST.ConnectCBs{
  private static final int REQ_ACCPICK = 1;
  private static final int REQ_CONNECT = 2;

  private static TextView mDispTxt;
  private static boolean mBusy;

  @Override
  protected void onCreate(Bundle bundle) { super.onCreate(bundle);
    setContentView(R.layout.activity_main);
    mDispTxt = (TextView)findViewById(R.id.tvDispText);
    if (bundle == null) {
      UT.init(this);
      if (!REST.init(this)) {
        startActivityForResult(AccountPicker.newChooseAccountIntent(null,
            null, new String[]{GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE}, true, null, null, null, null),
          REQ_ACCPICK);
      }
    }
  }

  @Override
  protected void onResume() {  super.onResume();
    REST.connect();
  }
  @Override
  protected void onPause() {  super.onPause();
    REST.disconnect();
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.menu_main, menu);
    return true;
  }
  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.action_create: {
        createTree(UT.time2Titl(null));
        return true;
      }
      case R.id.action_list: {
        testTree();
        return true;
      }
      case R.id.action_delete: {
        deleteTree();
        return true;
      }
      case R.id.action_account: {
        mDispTxt.setText("");
        startActivityForResult(AccountPicker.newChooseAccountIntent(
          null, null, new String[]{GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE}, true, null, null, null, null), REQ_ACCPICK);
        return true;
      }
    }
    return super.onOptionsItemSelected(item);
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    switch (request) {
      case REQ_CONNECT:
        if (result == RESULT_OK)
          REST.connect();
        else {                                                                       UT.lg("act result - NO AUTH");
          suicide(R.string.err_auth_nogo);  //---------------------------------->>>
        }
        break;
      case REQ_ACCPICK:
        if (data != null && data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME) != null)
          UT.AM.setEmail(data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME));
        if (!REST.init(this)) {                                                    UT.lg("act result - NO ACCOUNT");
          suicide(R.string.err_auth_accpick); //---------------------------------->>>
        }
        break;
      }
    super.onActivityResult(request, result, data);
  }

  // *** connection callbacks ***********************************************************
  @Override
  public void onConnOK() {
    mDispTxt.append("\n\nCONNECTED TO: " + UT.AM.getEmail());
  }

  @Override
  public void onConnFail(Exception ex) {
    if (ex == null) {                                                         UT.lg("connFail - UNSPECD 1");
      suicide(R.string.err_auth_dono);  return;  //---------------------------------->>>
    }
    if (ex instanceof UserRecoverableAuthIOException) {                        UT.lg("connFail - has res");
      startActivityForResult((((UserRecoverableAuthIOException) ex).getIntent()), REQ_CONNECT);
    } else if (ex instanceof GoogleAuthIOException) {                          UT.lg("connFail - SHA1?");
      if (ex.getMessage() != null) suicide(ex.getMessage());  //--------------------->>>
      else  suicide(R.string.err_auth_sha);  //---------------------------------->>>
    } else {                                                                  UT.lg("connFail - UNSPECD 2");
      suicide(R.string.err_auth_dono);  //---------------------------------->>>
    }
  }

  /**
   * creates a directory tree to house a text file
   * @param titl file name (confirms to 'yyMMdd-HHmmss' and it's name is used
   *             to create it's parent folder 'yyyy-MM' under a common root 'GDRTDemo'
   *             GDRTDemo ---+--- yyyy-MM ---+--- yyMMdd-HHmmss
   *                         |               +--- yyMMdd-HHmmss
   *                         +--- yyyy-MM ---+--- yyMMdd-HHmmss
   *                                         +--- yyMMdd-HHmmss
   *                                              ....
   */
  private void createTree(final String titl) {
    if (titl != null && !mBusy) {
      mDispTxt.setText("UPLOADING\n");

      new AsyncTask<Void, String, Void>() {
        private String findOrCreateFolder(String prnt, String titl){
          ArrayList<ContentValues> cvs = REST.search(prnt, titl, UT.MIME_FLDR);
          String id, txt;
          if (cvs.size() > 0) {
            txt = "found ";
            id =  cvs.get(0).getAsString(UT.GDID);
          } else {
            id = REST.createFolder(prnt, titl);
            txt = "created ";
          }
          if (id != null)
            txt += titl;
          else
            txt = "failed " + titl;
          publishProgress(txt);
          return id;
        }

        @Override
        protected Void doInBackground(Void... params) {
          mBusy = true;
          String rsid = findOrCreateFolder("root", UT.MYROOT);
          if (rsid != null) {
            rsid = findOrCreateFolder(rsid, UT.titl2Month(titl));
            if (rsid != null) {
              File fl = UT.str2File("content of " + titl, "tmp" );
              String id = null;
              if (fl != null) {
                id = REST.createFile(rsid, titl, UT.MIME_TEXT, fl);
                fl.delete();
              }
              if (id != null)
                publishProgress("created " + titl);
              else
                publishProgress("failed " + titl);
            }
          }
          return null;
        }
        @Override
        protected void onProgressUpdate(String... strings) { super.onProgressUpdate(strings);
          mDispTxt.append("\n" + strings[0]);
        }
        @Override
        protected void onPostExecute(Void nada) { super.onPostExecute(nada);
          mDispTxt.append("\n\nDONE");
          mBusy = false;
        }
      }.execute();
    }
  }

  /**
   *  scans folder tree created by this app listing folders / files, updating file's
   *  'description' meadata in the process
   */
  private void testTree() {
    if (!mBusy) {
      mDispTxt.setText("DOWNLOADING\n");
      new AsyncTask<Void, String, Void>() {

        private void iterate(ContentValues gfParent) {
          ArrayList<ContentValues> cvs = REST.search(gfParent.getAsString(UT.GDID), null, null);
          if (cvs != null) for (ContentValues cv : cvs) {
            String gdid = cv.getAsString(UT.GDID);
            String titl = cv.getAsString(UT.TITL);

            if (REST.isFolder(cv)) {
              publishProgress(titl);
              iterate(cv);
            } else {
              byte[] buf = REST.read(gdid);
              if (buf == null)
                titl += " failed";
              publishProgress(titl);
              String str = buf == null ? "" : new String(buf);
              File fl = UT.str2File(str + "\n updated " + UT.time2Titl(null), "tmp" );
              if (fl != null) {
                String desc = "seen " + UT.time2Titl(null);
                REST.update(gdid, null, null, desc, fl);
                fl.delete();
              }
            }
          }
        }

        @Override
        protected Void doInBackground(Void... params) {
          mBusy = true;
          ArrayList<ContentValues> gfMyRoot = REST.search("root", UT.MYROOT, null);
          if (gfMyRoot != null && gfMyRoot.size() == 1 ){
            publishProgress(gfMyRoot.get(0).getAsString(UT.TITL));
            iterate(gfMyRoot.get(0));
          }
          return null;
        }

        @Override
        protected void onProgressUpdate(String... strings) {
          super.onProgressUpdate(strings);
          mDispTxt.append("\n" + strings[0]);
        }

        @Override
        protected void onPostExecute(Void nada) {
          super.onPostExecute(nada);
          mDispTxt.append("\n\nDONE");
          mBusy = false;
        }
      }.execute();
    }
  }

  /**
   *  scans folder tree created by this app deleting folders / files in the process
   */
  private void deleteTree() {
    if (!mBusy) {
      mDispTxt.setText("DELETING\n");
      new AsyncTask<Void, String, Void>() {

        private void iterate(ContentValues gfParent) {
          ArrayList<ContentValues> cvs = REST.search(gfParent.getAsString(UT.GDID), null, null);
          if (cvs != null) for (ContentValues cv : cvs) {
            String titl = cv.getAsString(UT.TITL);
            String gdid = cv.getAsString(UT.GDID);
            if (REST.isFolder(cv))
              iterate(cv);
            publishProgress("  " + titl + (REST.trash(gdid) ? " OK" : " FAIL"));
          }
        }

        @Override
        protected Void doInBackground(Void... params) {
          mBusy = true;
          ArrayList<ContentValues> gfMyRoot = REST.search("root", UT.MYROOT, null);
          if (gfMyRoot != null && gfMyRoot.size() == 1 ){
            ContentValues cv = gfMyRoot.get(0);
            iterate(cv);
            String titl = cv.getAsString(UT.TITL);
            String gdid = cv.getAsString(UT.GDID);
            publishProgress("  " + titl + (REST.trash(gdid) ? " OK" : " FAIL"));
          }
          return null;
        }

        @Override
        protected void onProgressUpdate(String... strings) {
          super.onProgressUpdate(strings);
          mDispTxt.append("\n" + strings[0]);
        }

        @Override
        protected void onPostExecute(Void nada) {
          super.onPostExecute(nada);
          mDispTxt.append("\n\nDONE");
          mBusy = false;
        }
      }.execute();
    }
  }

  private void suicide(int rid) {
    UT.AM.setEmail(null);
    Toast.makeText(this, rid, Toast.LENGTH_LONG).show();
    finish();
  }
  private void suicide(String msg) {
    UT.AM.setEmail(null);
    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    finish();
  }
}
