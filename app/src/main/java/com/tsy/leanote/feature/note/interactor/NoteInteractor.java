package com.tsy.leanote.feature.note.interactor;

import android.content.Context;

import com.tsy.leanote.MyApplication;
import com.tsy.leanote.R;
import com.tsy.leanote.constant.EnvConstant;
import com.tsy.leanote.feature.note.bean.Note;
import com.tsy.leanote.feature.note.contract.NoteContract;
import com.tsy.leanote.feature.user.bean.UserInfo;
import com.tsy.leanote.greendao.NoteDao;
import com.tsy.sdk.myokhttp.MyOkHttp;
import com.tsy.sdk.myokhttp.response.JsonResponseHandler;
import com.tsy.sdk.myutil.NetworkUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by tsy on 2016/12/23.
 */

public class NoteInteractor implements NoteContract.Interactor {

    private final String API_SYNC = "/api/note/getSyncNotes";       //获取需要同步的笔记
    private final String API_GET_NOTE = "/api/note/getNotes";       //获得某笔记本下的笔记

    private Object mTag;
    private Context mContext;
    private MyOkHttp mMyOkHttp;
    private NoteDao mNoteDao;

    public NoteInteractor() {
        this(null);
    }

    public NoteInteractor(Object tag) {
        mTag = tag;
        mContext = MyApplication.getInstance().getContext();
        mMyOkHttp = MyApplication.getInstance().getMyOkHttp();
        mNoteDao = MyApplication.getInstance().getDaoSession().getNoteDao();
    }

    /**
     * 获取notebookid下的notes
     * @param userInfo 当前登录用户
     * @param notebookid notebook id
     * @param callback
     */
    @Override
    public void getNotesByNotebookId(UserInfo userInfo, String notebookid, final NoteContract.GetNotesCallback callback) {
        if(!NetworkUtils.checkNetworkConnect(mContext)) {
            callback.onFailure(mContext.getString(R.string.app_no_network));
            return;
        }

        String url = EnvConstant.HOST + API_GET_NOTE;

        mMyOkHttp.get()
                .url(url)
                .addParam("token", userInfo.getToken())
                .addParam("notebookId", notebookid)
                .tag(mTag)
                .enqueue(new JsonResponseHandler() {
                    @Override
                    public void onSuccess(int statusCode, JSONObject response) {
                        if(response.has("Ok") && !response.optBoolean("Ok", false)) {
                            callback.onFailure(response.optString("Msg"));
                        }
                    }

                    @Override
                    public void onSuccess(int statusCode, JSONArray response) {
                        List<Note> notes = new ArrayList<>();

                        for (int i = 0; i < response.length(); i ++) {
                            JSONObject note_json = response.optJSONObject(i);
                            Note note = new Note();
                            note.setNoteid(note_json.optString("NoteId"));
                            note.setNotebookid(note_json.optString("NotebookId"));
                            note.setUid(note_json.optString("UserId"));
                            note.setTitle(note_json.optString("Title"));
                            note.setContent(note_json.optString("Content"));
                            note.setIs_markdown(note_json.optBoolean("IsMarkdown"));
                            note.setIs_blog(note_json.optBoolean("IsBlog"));
                            note.setIs_trash(note_json.optBoolean("IsTrash"));
                            note.setCreated_time(note_json.optString("CreatedTime"));
                            note.setUpdated_time(note_json.optString("UpdatedTime"));
                            note.setPublic_time(note_json.optString("PublicTime"));
                            note.setUsn(note_json.optInt("Usn"));

                            notes.add(note);
                        }
                        mNoteDao.insertInTx(notes);
                        callback.onSuccess(notes);
                    }

                    @Override
                    public void onFailure(int statusCode, String error_msg) {
                        callback.onFailure(error_msg);
                    }
                });
    }

    /**
     * 同步
     * @param userInfo 当前登录用户
     * @param callback
     */
    @Override
    public void sync(final UserInfo userInfo, final NoteContract.GetNotesCallback callback) {
        if(!NetworkUtils.checkNetworkConnect(mContext)) {
            callback.onFailure(mContext.getString(R.string.app_no_network));
            return;
        }

        String url = EnvConstant.HOST + API_SYNC;

        mMyOkHttp.get()
                .url(url)
                .addParam("token", userInfo.getToken())
                .addParam("afterUsn", String.valueOf(userInfo.getLast_usn()))
                .addParam("maxEntry", "1000")
                .tag(mTag)
                .enqueue(new JsonResponseHandler() {
                    @Override
                    public void onSuccess(int statusCode, JSONObject response) {
                        if(response.has("Ok") && !response.optBoolean("Ok", false)) {
                            callback.onFailure(response.optString("Msg"));
                        }
                    }

                    @Override
                    public void onSuccess(int statusCode, JSONArray response) {
                        List<Note> notes = new ArrayList<>();

                        for (int i = 0; i < response.length(); i ++) {
                            JSONObject note_json = response.optJSONObject(i);
                            Note note = new Note();
                            note.setNoteid(note_json.optString("NoteId"));
                            note.setNotebookid(note_json.optString("NotebookId"));
                            note.setUid(note_json.optString("UserId"));
                            note.setTitle(note_json.optString("Title"));
                            note.setContent(note_json.optString("Content"));
                            note.setIs_markdown(note_json.optBoolean("IsMarkdown"));
                            note.setIs_blog(note_json.optBoolean("IsBlog"));
                            note.setIs_trash(note_json.optBoolean("IsTrash"));
                            note.setCreated_time(note_json.optString("CreatedTime"));
                            note.setUpdated_time(note_json.optString("UpdatedTime"));
                            note.setPublic_time(note_json.optString("PublicTime"));
                            note.setUsn(note_json.optInt("Usn"));

                            if(userInfo.getLast_usn() > 0) {        //如果已经有则更新数据
                                Note cur_note = getNote(note_json.optString("NoteId"));
                                if(cur_note != null) {
                                    note.setId(cur_note.getId());
                                }
                            }
                            notes.add(note);
                        }
                        mNoteDao.insertOrReplaceInTx(notes);
                        callback.onSuccess(notes);
                    }

                    @Override
                    public void onFailure(int statusCode, String error_msg) {
                        callback.onFailure(error_msg);
                    }
                });
    }

    private Note getNote(String noteid) {
        List<Note> notes = mNoteDao.queryBuilder()
                .where(NoteDao.Properties.Noteid.eq(noteid))
                .list();
        if(notes != null && notes.size() > 0) {
            return notes.get(0);
        }

        return null;
    }
}