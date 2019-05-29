package fr.rostand.drone.model;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import dji.common.camera.SettingsDefinitions;
import dji.common.error.DJICameraError;
import dji.common.error.DJIError;
import dji.common.util.CommonCallbacks;
import dji.sdk.media.DownloadListener;
import dji.sdk.media.FetchMediaTask;
import dji.sdk.media.FetchMediaTaskContent;
import dji.sdk.media.FetchMediaTaskScheduler;
import dji.sdk.media.MediaFile;
import dji.sdk.media.MediaManager;
import dji.thirdparty.afinal.core.AsyncTask;

public class DroneMediaManager {
    private static final String TAG = "DroneMediaManager";

    private Context mContext;
    private static DroneMediaManager instance;

    // Drone variables
    private MediaManager mMediaManager;
    private List<MediaFile> mFileList = new ArrayList<>();

    private MediaManager.FileListState mCurrentFileListState = MediaManager.FileListState.UNKNOWN;
    private FetchMediaTaskScheduler mFetchMediaTaskScheduler;

    private File destDir = new File(Environment.getExternalStorageDirectory().getPath() + "/drone/");
    private int currentProgress = -1;

    private MediaManager.FileListStateListener mUpdateFileListStateListener = state -> {
        mCurrentFileListState = state;
    };

    private MutableLiveData<List<MediaFile>> mMediaFileList = new MutableLiveData<>();

    /**
     * Constructor
     */
    private DroneMediaManager(Context context) {
        this.mContext = context;
    }

    public static DroneMediaManager getInstance(Context context) {
        if (instance == null) {
            instance = new DroneMediaManager(context);
        }
        return instance;
    }

    /**
     * Getters and setters
     */
    public LiveData<List<MediaFile>> getMediaFileList() {
        return mMediaFileList;
    }

    public void setMediaFileList(List<MediaFile> mediaFileList) {
        mMediaFileList.postValue(mediaFileList);
    }

    /**
     * Handling methods
     */
    public void initComponents() {
        if (Drone.getProductInstance() == null) {
            setMediaFileList(new ArrayList<>());
        } else {
            if (null != Drone.getCameraInstance() && Drone.getCameraInstance().isMediaDownloadModeSupported()) {
                mMediaManager = Drone.getCameraInstance().getMediaManager();
                if (null != mMediaManager) {
                    mMediaManager.addUpdateFileListStateListener(this.mUpdateFileListStateListener);
                    Drone.getCameraInstance().setMode(SettingsDefinitions.CameraMode.MEDIA_DOWNLOAD, djiError -> {
                        if (djiError == null) {
                            Log.d(TAG, "Set camera mode success");
                            getFileList();
                        } else {
                            Log.d(TAG, "Set camera mode failed");
                            showToast("Set camera mode failed");
                        }
                    });

                    mFetchMediaTaskScheduler = mMediaManager.getScheduler();
                }
            }
        }
    }

    public void destroyComponents() {
        if (mMediaManager != null) {
            mMediaManager.stop(null);
            mMediaManager.removeFileListStateCallback(this.mUpdateFileListStateListener);
            mMediaManager.exitMediaDownloading();
            if (mFetchMediaTaskScheduler != null) {
                mFetchMediaTaskScheduler.removeAllTasks();
            }
        }

        Drone.getCameraInstance().setMode(SettingsDefinitions.CameraMode.SHOOT_PHOTO, new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError mError) {
                if (mError != null){
                    Log.d(TAG, "Set shoot photo mode failed : " + mError.getDescription());
                }
            }
        });

        if (mFileList != null) {
            setMediaFileList(new ArrayList<>());
        }
    }

    private void showToast(final String toastMsg) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(() -> Toast.makeText(mContext, toastMsg, Toast.LENGTH_SHORT).show());
    }

    /**
     * Drone management methods
     */
    public void getFileList() {
        mMediaManager = Drone.getCameraInstance().getMediaManager();
        if (mMediaManager != null) {
            if ((mCurrentFileListState == MediaManager.FileListState.SYNCING) || (mCurrentFileListState == MediaManager.FileListState.DELETING)){
                Log.d(TAG, "Media Manager is busy.");
            }else{
                mMediaManager.refreshFileListOfStorageLocation(SettingsDefinitions.StorageLocation.SDCARD, djiError -> {
                    if (null == djiError) {
                        //Reset data
                        if (mCurrentFileListState != MediaManager.FileListState.INCOMPLETE) {
                            setMediaFileList(new ArrayList<>());
                        }

                        mFileList = mMediaManager.getSDCardFileListSnapshot();
                        Collections.sort(mFileList, new Comparator<MediaFile>() {
                            @Override
                            public int compare(MediaFile lhs, MediaFile rhs) {
                                if (lhs.getTimeCreated() < rhs.getTimeCreated()) {
                                    return 1;
                                } else if (lhs.getTimeCreated() > rhs.getTimeCreated()) {
                                    return -1;
                                }
                                return 0;
                            }
                        });

                        mFetchMediaTaskScheduler.resume(error -> {
                            if (error == null) {
                                getThumbnails();
                            }
                        });

                        setMediaFileList(mFileList);
                    } else {
                        Log.d(TAG, "Get media file list failed : " + djiError.getDescription());
                    }
                });

            }
        }
    }

    public void getThumbnails() {
        if (mFileList.size() <= 0) {
            Log.d(TAG, "No File info for downloading thumbnails");
            return;
        }

        for (int i = 0; i < mFileList.size(); i++) {
            getThumbnailByIndex(i);
        }
    }

    private FetchMediaTask.Callback taskCallback = new FetchMediaTask.Callback() {
        @Override
        public void onUpdate(MediaFile file, FetchMediaTaskContent option, DJIError error) {
            if (null == error) {
                if (option == FetchMediaTaskContent.PREVIEW) {

                }

                if (option == FetchMediaTaskContent.THUMBNAIL) {

                }
            } else {
                Log.d(TAG, "Fetch media task failed : " + error.getDescription());
            }
        }
    };

    public void getThumbnailByIndex(final int index) {
        FetchMediaTask task = new FetchMediaTask(mFileList.get(index), FetchMediaTaskContent.THUMBNAIL, taskCallback);
        mFetchMediaTaskScheduler.moveTaskToEnd(task);
    }

    private void addMediaTask(final MediaFile mediaFile) {
        final FetchMediaTaskScheduler scheduler = mMediaManager.getScheduler();
        final FetchMediaTask task =
                new FetchMediaTask(mediaFile, FetchMediaTaskContent.PREVIEW, new FetchMediaTask.Callback() {
                    @Override
                    public void onUpdate(final MediaFile mediaFile, FetchMediaTaskContent fetchMediaTaskContent, DJIError error) {
                        if (null == error) {
                            if (mediaFile.getPreview() != null) {
                                AsyncTask.execute(() -> {
                                    Bitmap preview = mediaFile.getPreview();
                                });
                            } else {
                                Log.d(TAG, "Null bitmap !");
                            }
                        } else {
                            Log.d(TAG, "Fetch preview image failed : " + error.getDescription());
                        }
                    }
                });

        scheduler.resume(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                if (error == null) {
                    scheduler.moveTaskToNext(task);
                } else {
                    Log.d(TAG, "resume scheduler failed : " + error.getDescription());
                }
            }
        });
    }

    public void downloadFileByIndex(final int index){
        if ((mFileList.get(index).getMediaType() == MediaFile.MediaType.PANORAMA) || (mFileList.get(index).getMediaType() == MediaFile.MediaType.SHALLOW_FOCUS)) {
            return;
        }

        mFileList.get(index).fetchFileData(destDir, null, new DownloadListener<String>() {
            @Override
            public void onFailure(DJIError error) {
                Log.d(TAG, "Download File Failed" + error.getDescription());
                currentProgress = -1;
            }

            @Override
            public void onProgress(long total, long current) {
                int tmpProgress = (int) (1.0 * current / total * 100);
                if (tmpProgress != currentProgress) {
                    currentProgress = tmpProgress;
                }
            }

            @Override
            public void onRateUpdate(long total, long current, long persize) {
                int tmpProgress = (int) (1.0 * current / total * 100);
                if (tmpProgress != currentProgress) {
                    currentProgress = tmpProgress;
                }
            }

            @Override
            public void onStart() {
                currentProgress = -1;
            }

            @Override
            public void onSuccess(String filePath) {
                Log.d(TAG, "Download file success : " + filePath);
                currentProgress = -1;
            }
        });
    }

    public void deleteFileByIndex(final int index) {
        ArrayList<MediaFile> fileToDelete = new ArrayList<>();
        if (mFileList.size() > index) {
            fileToDelete.add(mFileList.get(index));
            mMediaManager.deleteFiles(fileToDelete, new CommonCallbacks.CompletionCallbackWithTwoParam<List<MediaFile>, DJICameraError>() {
                @Override
                public void onSuccess(List<MediaFile> x, DJICameraError y) {
                    Log.d(TAG, "Delete file success");
                    AsyncTask.execute(() -> {
                        MediaFile file = mFileList.remove(index);
                    });
                }

                @Override
                public void onFailure(DJIError error) {
                    Log.d(TAG, "Delete file failed");
                }
            });

            setMediaFileList(mFileList);
        }
    }
}