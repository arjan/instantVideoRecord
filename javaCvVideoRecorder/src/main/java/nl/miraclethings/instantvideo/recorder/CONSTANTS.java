package nl.miraclethings.instantvideo.recorder;

import android.os.Environment;

public class CONSTANTS {
	
	public final static String FILE_START_NAME = "VMS_";
	public final static String VIDEO_EXTENSION = ".mp4";
	public final static String DCIM_FOLDER = "/DCIM";
	public final static String CAMERA_FOLDER = "/Camera/Sourab";
	public final static String TEMP_FOLDER = "/Temp";
	public final static String CAMERA_FOLDER_PATH = Environment.getExternalStorageDirectory().toString() + CONSTANTS.DCIM_FOLDER + CONSTANTS.CAMERA_FOLDER;
	public final static String TEMP_FOLDER_PATH = Environment.getExternalStorageDirectory().toString() + CONSTANTS.DCIM_FOLDER + CONSTANTS.CAMERA_FOLDER + CONSTANTS.TEMP_FOLDER;
	public final static String  VIDEO_CONTENT_URI = "content://media/external/video/media";
	
	public final static String KEY_DELETE_FOLDER_FROM_SDCARD = "deleteFolderFromSDCard";
	
	public final static int RESOLUTION_HIGH = 1300;
	public final static int RESOLUTION_MEDIUM = 500;
	public final static int RESOLUTION_LOW = 180;
	
	public final static int RESOLUTION_HIGH_VALUE = 2;
	public final static int RESOLUTION_MEDIUM_VALUE = 1;
	public final static int RESOLUTION_LOW_VALUE = 0;
}
