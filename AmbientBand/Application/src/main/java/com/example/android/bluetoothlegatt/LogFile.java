/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.bluetoothlegatt;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.os.Handler;
import android.os.StatFs;
import android.text.Spanned;
import android.text.SpannedString;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Observable;


/**
 * Utility class that handles writing, reading and cleaning up files where we
 * log the activities that where detected by the activity detection service.
 */
public class LogFile extends Observable {
	
	private long lastUpdate = -1;
	
	Handler handler;

//	private static long UPLOAD_INTERVAL = 1000 * 10; // upload data every 10 sec in online version

    // Store a context for handling files
    private final Context mContext;

    // Store an object that can "print" to a file
    private PrintWriter mActivityWriter;

    // Store a File handle
    private File mLogFile;

    // Store the shared preferences repository handle
    private SharedPreferences mPrefs;

    // Store the current file number and name
    private int mFileNumber;
    private String mFileName;
    
    private static String mSuffix = ".txt";

    // Store an sLogFileInstance of the log file
    private static LogFile sLogFileInstance = null;

    private int mLogFileNumber = 0;
    
    /**
     * Singleton that ensures that only one sLogFileInstance of the LogFile exists at any time
     *
     * @param context A Context for the current app
     */
    private LogFile(Context context) {

        // Get the context from the caller
        mContext = context;


        // Create a timestamp
        String dateString = new SimpleDateFormat("dd_MM_yyyy", Locale.US).format(new Date());

        // Create the file name by sprintf'ing its parts into the filename string.

        mFileName = "climbaware-"+ dateString.toString() + "-" + mLogFileNumber;


        // Commit the updates2

        // Create the log file
        mLogFile = createLogFile(mFileName + mSuffix);


    }
    public void createFreshLogFile() {
        int max = -1;
        for(File f : getLogFiles()) {
            String[] parts = f.getName().split("-");
            int i = Integer.getInteger(parts[parts.length-1].split(".")[0]);
            if(i > max)
                max = i;
        }
        mLogFileNumber = max + 1;

        String dateString = new SimpleDateFormat("dd_MM_yyyy", Locale.US).format(new Date());
        mFileName = "climbaware-"+ dateString.toString() + "-" + mLogFileNumber;
        mLogFile = createLogFile(mFileName + mSuffix);
    }

    public static LogFile getInstance(Context context, String suffix) {
    	mSuffix = suffix;
    	return getInstance(context);
    }

    /**
     * Create an sLogFileInstance of log file, or return the current sLogFileInstance
     *
     * @param context A Context for the current app
     *
     * @return An sLogFileInstance of this class
     */
    public static LogFile getInstance(Context context) {

        if (sLogFileInstance == null) {
            sLogFileInstance = new LogFile(context);
        }
        return sLogFileInstance;
    }


    /**
     * Log a message to the log file
     */
    public void log(String message) {

//		 Log.d(ActivityUtils.APPTAG, "write to log: " + message);

    	
        // Start a print writer for the log file
        initLogWriter();

        // Print a log message
        mActivityWriter.println(message);

        // Flush buffers
        mActivityWriter.flush();

    }

    /**
     * Loads data from the log file.
     */
    public List<Spanned> loadLogFile() throws IOException {

        // Get a new List of spanned strings
        List<Spanned> content = new ArrayList<Spanned>();

        // If no log file exists yet, return the empty List
        if (!mLogFile.exists()) {
            return content;
        }

        // Create a new buffered file reader based on the log file
        BufferedReader reader = new BufferedReader(new FileReader(mLogFile));

        // Get a String instance to hold input from the log file
        String line;

        /*
         * Read until end-of-file from the log file, and store the input line as a
         * spanned string in the List
         */
        while ((line = reader.readLine()) != null) {
            content.add(new SpannedString(line));
        }

        // Close the file
        reader.close();

        // Return the data from the log file
        return content;
    }

    /**
     * Creates an object that writes human-readable lines of text to a file
     */
    private void initLogWriter() {

        // Catch IO exceptions
        try {

            // If the writer is still open, close it
            if (mActivityWriter != null) {
                mActivityWriter.close();
            }

            // Create a new writer for the log file
            mActivityWriter = new PrintWriter(new FileWriter(mLogFile, true));

        // If an IO exception occurs, print a stack trace
        } catch (IOException e) {
                e.printStackTrace();
        }
    }
    
    public File[] getLogFiles() {

    	FilenameFilter filter = new FilenameFilter(){
			@Override
			public boolean accept(File dir, String filename) {
				if(filename.endsWith(".txt"))
					return true;
				else 
					return false;
			}
        };
        
    	File[] files = mContext.getFilesDir().listFiles(filter);

        // external storage is available
        if(getDestinationDirectory() != mContext.getFilesDir()) {
	    	
	    	File[] filesExternal = getDestinationDirectory().listFiles(filter);
	    	
	    	ArrayList<File> all = new ArrayList<File>();
	    	all.addAll(Arrays.asList(filesExternal));
	    	all.addAll(Arrays.asList(files));
	    	
	    	File[] allfiles = new File[files.length+filesExternal.length];
	    	all.toArray(allfiles);
	    	
	    	// sort by date
	    	Arrays.sort(allfiles, new Comparator<File>() {
	
				@Override
				public int compare(File lhs, File rhs) {
					if(lhs.lastModified() < rhs.lastModified())
						return -1;
					else if(lhs.lastModified() > rhs.lastModified())
						return 1;
					else return 0;
								
				}
	    		
	    	});
	    	
	    	return allfiles;
        } else { // external storage not available
        	return files;
        }
    }

    /**
     *  Checks if external storage is available for read and write
     */
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }
    
    /* Checks if external storage is available to at least read */
    public boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state) ||
            Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            return true;
        }
        return false;
    }
    
    /*
     * in number bytes
     */
	public long spaceLeft() {
	    StatFs stat = new StatFs(this.getDestinationDirectory().getPath());
	    long bytesAvailable = (long)stat.getBlockSize() * (long)stat.getAvailableBlocks();
	    return bytesAvailable;
	}
	
	public long getDataLength() {		
		long lines = 0;
		for(File f : getLogFiles()) {
			lines += countLines(f);
			
		}
		return lines;
	}
	
	private static int countLines(File file) {
	    LineNumberReader reader = null;
	    try {
	        reader = new LineNumberReader(new FileReader(file));
	        while ((reader.readLine()) != null);
	           reader.close();
	        return reader.getLineNumber();
	    } catch (Exception ex) {
	        return -1;
	    }
	}

    private File getDestinationDirectory() {
    	File dir = mContext.getFilesDir();
    	if(isExternalStorageReadable() && isExternalStorageWritable()) {
    		dir = Environment.getExternalStoragePublicDirectory(mContext.getString(R.string.logfile_foldername));
    		dir.mkdir();
    		dir.setReadable(true);
    		dir.setWritable(true);
    	}	
    	
    	return dir;
    }

    /**
     * Returns a new file object for the specified filename.
     *
     * @return A File for the given file name
     */
    private File createLogFile(String filename) {
    	File dir = getDestinationDirectory();

        // Create a new file in the app's directory
        File newFile = new File(dir, filename);
        
        // return the new file handle
        return newFile;

    }
    
    public File getLogFile() {
    	return mLogFile;
    }

	



}
