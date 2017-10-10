package com.artifex.mupdfdemo;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.example.tao_oat.myapplication.R;

import java.io.File;
import java.io.InputStream;


public class MuPDFActivity extends Activity implements FilePicker.FilePickerSupport, View.OnClickListener {
    @Override
    public void performPickFor(FilePicker picker) {

    }

    /* The core rendering instance */
    enum TopBarMode {
        Main, Search, Annot, Delete, More, Accept
    }

    ;

    private final int OUTLINE_REQUEST = 0;
    private final int PROOF_REQUEST = 3;
    private MuPDFCore core;
    private String mFileName;
    private MuPDFReaderView mDocView;
    private EditText mPasswordView;
    private TopBarMode mTopBarMode = TopBarMode.Main;
    private SearchTask mSearchTask;
    private boolean mLinkHighlight = false;
    private boolean mReflow = false;
    private String mProofFile;

    private String title;
    private ImageView backImg;
    private TextView titleTxt;
    private TextView right_txt;
    private LinearLayout share_layout;
    private ImageView share_circle_img, share_friend_img;

    private String compressImg, shareUrl, rDesc;
    private int isShare = 0;

    private MuPDFCore openFile(String path) {
        int lastSlashPos = path.lastIndexOf('/');
        mFileName = new String(lastSlashPos == -1
                ? path
                : path.substring(lastSlashPos + 1));
        try {
            core = new MuPDFCore(this, path);
            // New file: drop the old outline data
            OutlineActivityData.set(null);
        } catch (Exception e) {
            return null;
        } catch (OutOfMemoryError e) {
            //  out of memory is not an Exception, so we catch it separately.
            return null;
        }
        return core;
    }

    private MuPDFCore openBuffer(byte buffer[], String magic) {
        try {
            core = new MuPDFCore(this, buffer, magic);
            // New file: drop the old outline data
            OutlineActivityData.set(null);
        } catch (Exception e) {
            return null;
        }
        return core;
    }

    //  determine whether the current activity is a proofing activity.
    public boolean isProofing() {
        String format = core.fileFormat();
        return (format.equals("GPROOF"));
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        title = getIntent().getStringExtra("title");
        compressImg = getIntent().getStringExtra("compressImg");
        shareUrl = getIntent().getStringExtra("shareUrl");
        rDesc = getIntent().getStringExtra("rDesc");

        if (core == null) {
            core = (MuPDFCore) getLastNonConfigurationInstance();

            if (savedInstanceState != null && savedInstanceState.containsKey("FileName")) {
                mFileName = savedInstanceState.getString("FileName");
            }
        }
        if (core == null) {
            Intent intent = getIntent();
            byte buffer[] = null;

            if (Intent.ACTION_VIEW.equals(intent.getAction())) {
                Uri uri = intent.getData();
                if (uri.toString().startsWith("content://")) {
                    String reason = null;
                    try {
                        InputStream is = getContentResolver().openInputStream(uri);
                        int len = is.available();
                        buffer = new byte[len];
                        is.read(buffer, 0, len);
                        is.close();
                    } catch (OutOfMemoryError e) {
                        reason = e.toString();
                    } catch (Exception e) {
                        // Handle view requests from the Transformer Prime's file manager
                        // Hopefully other file managers will use this same scheme, if not
                        // using explicit paths.
                        // I'm hoping that this case below is no longer needed...but it's
                        // hard to test as the file manager seems to have changed in 4.x.
                        try {
                            Cursor cursor = getContentResolver().query(uri, new String[]{"_data"}, null, null, null);
                            if (cursor.moveToFirst()) {
                                String str = cursor.getString(0);
                                if (str == null) {
                                    reason = "Couldn't parse data in intent";
                                } else {
                                    uri = Uri.parse(str);
                                }
                            }
                        } catch (Exception e2) {
                            reason = e2.toString();
                        }
                    }
                }
                if (buffer != null) {
                    core = openBuffer(buffer, intent.getType());
                } else {
                    String path = Uri.decode(uri.getEncodedPath());
                    if (path == null) {
                        path = uri.toString();
                    }
                    core = openFile(path);
                }
                SearchTaskResult.set(null);
            }
            if (core != null && core.needsPassword()) {
                requestPassword(savedInstanceState);
                return;
            }
            if (core != null && core.countPages() == 0) {
                core = null;
            }
        }

        createUI(savedInstanceState);
    }

    public void requestPassword(final Bundle savedInstanceState) {
        mPasswordView = new EditText(this);
        mPasswordView.setInputType(EditorInfo.TYPE_TEXT_VARIATION_PASSWORD);
        mPasswordView.setTransformationMethod(new PasswordTransformationMethod());
    }

    public void createUI(Bundle savedInstanceState) {
        setContentView(R.layout.activity_pdf_show);
        titleTxt = (TextView) findViewById(R.id.title_txt);
        titleTxt.setText(title);
        backImg = (ImageView) findViewById(R.id.left_img);
        right_txt = (TextView) findViewById(R.id.right_txt);
        right_txt.setVisibility(View.GONE);

        backImg.setOnClickListener(this);

        if (core == null)
            return;

        // Now create the UI.
        // First create the document view
        mDocView = new MuPDFReaderView(this) {
            @Override
            protected void onMoveToChild(int i) {
                if (core == null)
                    return;
                super.onMoveToChild(i);
            }

            @Override
            protected void onTapMainDocArea() {
            }

            @Override
            protected void onDocMotion() {
            }

            @Override
            protected void onHit(Hit item) {
                switch (mTopBarMode) {
                    case Annot:
                        if (item == Hit.Annotation) {
                            mTopBarMode = TopBarMode.Delete;
                        }
                        break;
                    case Delete:
                        mTopBarMode = TopBarMode.Annot;
                        // fall through
                    default:
                        // Not in annotation editing mode, but the pageview will
                        // still select and highlight hit annotations, so
                        // deselect just in case.
                        MuPDFView pageView = (MuPDFView) mDocView.getDisplayedView();
                        if (pageView != null)
                            pageView.deselectAnnotation();
                        break;
                }
            }
        };
        mDocView.setAdapter(new MuPDFPageAdapter(this, this, core));

        mSearchTask = new SearchTask(this, core) {
            @Override
            protected void onTextFound(SearchTaskResult result) {
                SearchTaskResult.set(result);
                // Ask the ReaderView to move to the resulting page
                mDocView.setDisplayedViewIndex(result.pageNumber);
                // Make the ReaderView act on the change to SearchTaskResult
                // via overridden onChildSetup method.
                mDocView.resetupChildren();
            }
        };

        // Reenstate last state if it was recorded
        SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
        mDocView.setDisplayedViewIndex(prefs.getInt("page" + mFileName, 0));

        if (savedInstanceState != null && savedInstanceState.getBoolean("SearchMode", false))
            searchModeOn();

        if (savedInstanceState != null && savedInstanceState.getBoolean("ReflowMode", false))
            reflowModeSet(true);

        RelativeLayout layout = (RelativeLayout) findViewById(R.id.content_layout);
        layout.addView(mDocView);

        mDocView.setDisplayedViewIndex(0);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case OUTLINE_REQUEST:
                if (resultCode >= 0)
                    mDocView.setDisplayedViewIndex(resultCode);
                break;
            case PROOF_REQUEST:
                //  we're returning from a proofing activity

                if (mProofFile != null) {
                    core.endProof(mProofFile);
                    mProofFile = null;
                }

                //  return the top bar to default
                mTopBarMode = TopBarMode.Main;
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    public Object onRetainNonConfigurationInstance() {
        MuPDFCore mycore = core;
        core = null;
        return mycore;
    }

    private void reflowModeSet(boolean reflow) {
        mReflow = reflow;
        mDocView.setAdapter(mReflow ? new MuPDFReflowAdapter(this, core) : new MuPDFPageAdapter(this, this, core));
        if (reflow) setLinkHighlight(false);
        mDocView.refresh(mReflow);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        if (mFileName != null && mDocView != null) {
            outState.putString("FileName", mFileName);

            // Store current page in the prefs against the file name,
            // so that we can pick it up each time the file is loaded
            // Other info is needed only for screen-orientation change,
            // so it can go in the bundle
            SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
            SharedPreferences.Editor edit = prefs.edit();
            edit.putInt("page" + mFileName, mDocView.getDisplayedViewIndex());
            edit.commit();
        }
    }


    @Override
    protected void onPause() {
        super.onPause();



        if (mSearchTask != null)
            mSearchTask.stop();

        if (mFileName != null && mDocView != null) {
            SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
            SharedPreferences.Editor edit = prefs.edit();
            edit.putInt("page" + mFileName, mDocView.getDisplayedViewIndex());
            edit.commit();
        }
    }

    public void onDestroy() {
        if (mDocView != null) {
            mDocView.applyToChildren(new ReaderView.ViewMapper() {
                void applyToView(View view) {
                    ((MuPDFView) view).releaseBitmaps();
                }
            });
        }
        if (core != null)
            core.onDestroy();
        core = null;
        super.onDestroy();
    }

    private void setLinkHighlight(boolean highlight) {
        mLinkHighlight = highlight;
        // Inform pages of the change.
        mDocView.setLinksEnabled(highlight);
    }

    private void searchModeOn() {
        if (mTopBarMode != TopBarMode.Search) {
            mTopBarMode = TopBarMode.Search;
            //Focus on EditTextWidget
        }
    }

    @Override
    protected void onStart() {
        if (core != null) {
            core.startAlerts();
        }
        super.onStart();
    }

    @Override
    protected void onStop() {
        if (core != null) {
            core.stopAlerts();
        }
        super.onStop();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.left_img:
                finish();
                break;

            default:
                break;
        }
    }

}
