/*Rev1.7
 * 
 * 
 * 
 * Copyright (c) 2010, Shimmer Research, Ltd.
 * All rights reserved
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:

 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of Shimmer Research, Ltd. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.

 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * @author Jong Chern Lim
 * @date   November, 2013
 * 
 * Changes since 1.6
 * - cancel timers for log and stream upon disconnect
 * 
 * Changes since 1.5
 * - updates to constructors
 * 
 * Changes since 1.4.3
 * - remove responsetimer to ShimmerBluetooth
 *  
 * Changes since 1.4.2
 * - included call to isreadyforstreaming
 * - new object for callback method (msg_identifier 1 and 2 only)
 * - only runs connect() if mSerialPort==null
 * - added packet reception rate callback
 * 
 * Changes since 1.4
 * - updated states, and comments
 * 

 */

package com.shimmerresearch.pcdriver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import com.shimmerresearch.bluetooth.ShimmerBluetooth;
import com.shimmerresearch.driver.ObjectCluster;

import jssc.SerialPort;
import jssc.SerialPortException;


public class ShimmerPC extends ShimmerBluetooth{
	// Used by the constructor when the user intends to write new settings to the Shimmer device after connection
	SerialPort mSerialPort=null;
	ObjectCluster objectClusterTemp = null;
	Callable myUIThread;


	public final static int MSG_IDENTIFIER_STATE_CHANGE = 0;
	public final static int MSG_IDENTIFIER_NOTIFICATION_MESSAGE = 1; 
	public final static int MSG_IDENTIFIER_DATA_PACKET = 2;
	public final static int MSG_IDENTIFIER_PACKET_RECEPTION_RATE = 3;
	
	public final static int NOTIFICATION_STOP_STREAMING =0;
	public final static int NOTIFICATION_START_STREAMING =1;
	public final static int NOTIFICATION_FULLY_INITIALIZED =2;
	
	/**
	 * Constructor. Prepares a new Bluetooth session. Upon Connection the configuration of the device is read back and used. No device setup is done. To setup device see other Constructors.
	 * @param context  The UI Activity Context
	 * @param handler  A Handler to send messages back to the UI Activity
	 * @param myname  To allow the user to set a unique identifier for each Shimmer device
	 * @param countiousSync A boolean value defining whether received packets should be checked continuously for the correct start and end of packet.
	 */
	public ShimmerPC(String myName, Boolean continousSync) {
		mMyName=myName;
		mContinousSync=continousSync;
		mSetupDevice=false;
	}

	/**Shimmer 3 Constructor
	 * @param myname  To allow the user to set a unique identifier for each Shimmer device
	 * @param samplingRate Defines the sampling rate
	 * @param accelRange Defines the Acceleration range. Valid range setting values for the Shimmer 2 are 0 (+/- 1.5g), 1 (+/- 2g), 2 (+/- 4g) and 3 (+/- 6g). Valid range setting values for the Shimmer 2r are 0 (+/- 1.5g) and 3 (+/- 6g).
	 * @param gsrRange Numeric value defining the desired gsr range. Valid range settings are 0 (10kOhm to 56kOhm),  1 (56kOhm to 220kOhm), 2 (220kOhm to 680kOhm), 3 (680kOhm to 4.7MOhm) and 4 (Auto Range).
	 * @param setEnabledSensors Defines the sensors to be enabled (e.g. 'Shimmer.SENSOR_ACCEL|Shimmer.SENSOR_GYRO' enables the Accelerometer and Gyroscope)
	 * @param countiousSync A boolean value defining whether received packets should be checked continuously for the correct start and end of packet.
	 * @param enableLowPowerAccel Enables low power Accel on the wide range accelerometer
	 * @param enableLowPowerGyro Enables low power Gyro
	 * @param enableLowPowerMag Enables low power Mag
	 * @param gyroRange Sets the Gyro Range of the accelerometer
	 * @param magRange Sets the Mag Range
	 * @param exg1 Sets the register of EXG chip 1
	 * @param exg2 Setes the register of EXG chip 2
	 */
	public ShimmerPC(String myName, double samplingRate, int accelRange, int gsrRange, int setEnabledSensors, boolean continousSync, boolean enableLowPowerAccel, boolean enableLowPowerGyro, boolean enableLowPowerMag, int gyroRange, int magRange,byte[] exg1,byte[] exg2) {
		mState = STATE_NONE;
		mSamplingRate = samplingRate;
		mAccelRange = accelRange;
		mGSRRange = gsrRange;
		mSetEnabledSensors=setEnabledSensors;
		mMyName = myName;
		mSetupDevice = true;
		mContinousSync = continousSync;
		mLowPowerMag = enableLowPowerMag;
		mLowPowerAccel = enableLowPowerAccel;
		mLowPowerGyro = enableLowPowerGyro;
		mGyroRange = gyroRange;
		mMagGain = magRange;
		mSetupEXG = true;
		mEXG1Register = exg1;
		mEXG2Register = exg2;
	}
	
	/**
	*  Shimmer2, Constructor. Prepares a new Bluetooth session. Additional fields allows the device to be set up immediately.
	 * @param myname  To allow the user to set a unique identifier for each Shimmer device
	 * @param samplingRate Defines the sampling rate
	 * @param accelRange Defines the Acceleration range. Valid range setting values for the Shimmer 2 are 0 (+/- 1.5g), 1 (+/- 2g), 2 (+/- 4g) and 3 (+/- 6g). Valid range setting values for the Shimmer 2r are 0 (+/- 1.5g) and 3 (+/- 6g).
	 * @param gsrRange Numeric value defining the desired gsr range. Valid range settings are 0 (10kOhm to 56kOhm),  1 (56kOhm to 220kOhm), 2 (220kOhm to 680kOhm), 3 (680kOhm to 4.7MOhm) and 4 (Auto Range).
	 * @param setEnabledSensors Defines the sensors to be enabled (e.g. 'Shimmer.SENSOR_ACCEL|Shimmer.SENSOR_GYRO' enables the Accelerometer and Gyroscope)
	 * @param countiousSync A boolean value defining whether received packets should be checked continuously for the correct start and end of packet.
	 * @param magGain Set mag gain
	 */
	public ShimmerPC(String myName, double samplingRate, int accelRange, int gsrRange, int setEnabledSensors, boolean continousSync, int magGain) {
		mState = STATE_NONE;
		mSamplingRate = samplingRate;
		mAccelRange = accelRange;
		mMagGain = magGain;
		mGSRRange = gsrRange;
		mSetEnabledSensors=setEnabledSensors;
		mMyName = myName;
		mSetupDevice = true;
		mContinousSync = continousSync;
	}
	
	// Javadoc comment follows
    /**
     * @deprecated
     * The Shimmer constructor should only have one Shimmer2R constructor
     */
    @Deprecated
	public ShimmerPC( String myName, double samplingRate, int accelRange, int gsrRange, int setEnabledSensors, boolean continousSync) {
		mSamplingRate = samplingRate;
		mAccelRange = accelRange;
		mGSRRange = gsrRange;
		mSetEnabledSensors=setEnabledSensors;
		mMyName = myName;
		mSetupDevice = true;
		mContinousSync = continousSync;
	}
	
	/**
	 * Connect to device specified by address
	 * @param address  The comport of the device e.g. COM32, note device will have to be paired first
	 * @param empty  This is for forward compatibility, in the event a choice of library is offered, any string value can be entered now ~ does nothing
	 */
	@Override
	public synchronized void connect(final String address, String a) {
		if (mSerialPort==null){
		mMyBluetoothAddress = address;
		mSerialPort = new SerialPort(address);
		mListofInstructions.clear();
		mFirstTime=true;
		try {
			setState(STATE_CONNECTING);
			System.out.println("Port open: " + mSerialPort.openPort());
			System.out.println("Params set: " + mSerialPort.setParams(115200, 8, 1, 0));
			System.out.println("Port Status : " + Boolean.toString(mSerialPort.isOpened()));
			if (mIOThread != null) { 
				mIOThread = null;
				}
			if (mSerialPort.isOpened()){
				setState(STATE_CONNECTED);
				mIOThread = new IOThread();
				mIOThread.start();
				initialize();
			}
		}
		catch (SerialPortException ex){
			setState(STATE_NONE);
			connectionLost();
			System.out.println(ex);
		}
		}
	}
	
	
	@Override
	public boolean bytesToBeRead() {
		// TODO Auto-generated method stub
		try {
			if (mSerialPort.getInputBufferBytesCount()!=0){
				return true;
			}
		} catch (SerialPortException e) {
			// TODO Auto-generated catch block
			connectionLost();
			e.printStackTrace();
		}
		return false;
	}
	
	public int availableBytes(){
		try {
			return mSerialPort.getInputBufferBytesCount();
		} catch (SerialPortException e) {
			// TODO Auto-generated catch block
			connectionLost();
			e.printStackTrace();
			return 0;
		}
	}

	@Override
	public void writeBytes(byte[] data) {
		// TODO Auto-generated method stub
		try {
			mSerialPort.writeBytes(data);
		} catch (SerialPortException e) {
			// TODO Auto-generated catch block
			connectionLost();
			e.printStackTrace();
		}
	}

	@Override
	protected byte[] readBytes(int numberofBytes) {
		// TODO Auto-generated method stub
		try {
			if (mSerialPort.isOpened())
			{
			return(mSerialPort.readBytes(numberofBytes));
			} else {
				System.out.println("ALERT!!");
			}
		} catch (SerialPortException e) {
			// TODO Auto-generated catch block
			connectionLost();
			e.printStackTrace();
		}
		return null;
	}



	@Override
	protected void stop() {
		// TODO Auto-generated method stub
		disconnect();
	}




	@Override
	protected void isNowStreaming() {
		// TODO Auto-generated method stub
		// Send a notification msg to the UI through a callback (use a msg identifier notification message)
		// Do something here

		CallbackObject callBackObject = new CallbackObject(NOTIFICATION_START_STREAMING, getBluetoothAddress());
		myUIThread.callBackMethod(MSG_IDENTIFIER_NOTIFICATION_MESSAGE, callBackObject);
	}

	@Override
	protected byte readByte() {
		byte[] b = readBytes(1);
		return b[0];
	}

	@Override
	protected void inquiryDone() {
		// TODO Auto-generated method stub
		CallbackObject callBackObject = new CallbackObject(mState, getBluetoothAddress());
		myUIThread.callBackMethod(MSG_IDENTIFIER_STATE_CHANGE, callBackObject);
		isReadyForStreaming();
	}

	@Override
	protected void isReadyForStreaming() {
		// TODO Auto-generated method stub
		// Send msg fully initialized, send notification message,  
		// DO Something here
        mInitialized = true;
		CallbackObject callBackObject = new CallbackObject(NOTIFICATION_FULLY_INITIALIZED, getBluetoothAddress());
		myUIThread.callBackMethod(MSG_IDENTIFIER_NOTIFICATION_MESSAGE, callBackObject);
	}

	@Override
	protected void dataHandler(ObjectCluster ojc) {
		// TODO Auto-generated method stub
		myUIThread.callBackMethod(MSG_IDENTIFIER_PACKET_RECEPTION_RATE, getPacketReceptionRate());
		myUIThread.callBackMethod(MSG_IDENTIFIER_DATA_PACKET, ojc);
	}

	public byte[] returnRawData(){
		if (objectClusterTemp!=null){
			byte[] data= objectClusterTemp.mRawData;
			//objectClusterTemp = null;
			return data;
		
		}
		else 
			return null;
	}

	public void passCallback(Callable c) {
		// TODO Auto-generated method stub
		myUIThread = c;
	}
	
	public synchronized void disconnect(){
		if (mTimerToReadStatus!=null) {
			mTimerToReadStatus.cancel();
			mTimerToReadStatus.purge();
		}
		
		if (mTimer!=null){
			mTimer.cancel();
			mTimer.purge();
		}
		try {
			if (mIOThread != null) {
				mIOThread.stop = true;
				mIOThread = null;
				
			}
			mStreaming = false;
			mInitialized = false;
			if (mSerialPort != null && mSerialPort.isOpened()) {
				  mSerialPort.purgePort (1);
				  mSerialPort.purgePort (2);
				  mSerialPort.closePort ();
				}
			
			mSerialPort = null;
			setState(STATE_NONE);
		} catch (SerialPortException e) {
			// TODO Auto-generated catch block
			setState(STATE_NONE);
			e.printStackTrace();
		}
		CallbackObject callBackObject = new CallbackObject(mState, getBluetoothAddress());
		myUIThread.callBackMethod(MSG_IDENTIFIER_STATE_CHANGE, callBackObject);
	}

	@Override
	protected void sendStatusMsgPacketLossDetected() {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void connectionLost() {
		System.out.println("Connection Lost");
		try {
			if (mIOThread != null) {
				mIOThread.stop = true;
				mIOThread = null;
				
			}
			mStreaming = false;
			mInitialized = false;
			if (mSerialPort != null && mSerialPort.isOpened ()) {
				  mSerialPort.purgePort (1);
				  mSerialPort.purgePort (2);
				  mSerialPort.closePort ();
				}
			
			mSerialPort = null;
			setState(STATE_NONE);
		} catch (SerialPortException e) {
			// TODO Auto-generated catch block
			setState(STATE_NONE);
			e.printStackTrace();
		}	
		
	}

	@Override
	protected void sendStatusMSGtoUI(String msg) {
		// TODO Auto-generated method stub
		
	}

	
	public String message;
	@Override
	protected void printLogDataForDebugging(String msg) {
		// TODO Auto-generated method stub
		System.out.println(msg);
	}

	@Override
	protected void setState(int state) {
		// TODO Auto-generated method stub
		mState = state;
		// Give the new state to the UI through a callback (use a msg identifier state change)
		// Do something here
	}
	
	@Override
	protected void hasStopStreaming() {
		// TODO Auto-generated method stub
		// Send a notification msg to the UI through a callback (use a msg identifier notification message)
				// Do something here
		CallbackObject callBackObject = new CallbackObject(NOTIFICATION_STOP_STREAMING, getBluetoothAddress());
		myUIThread.callBackMethod(MSG_IDENTIFIER_NOTIFICATION_MESSAGE, callBackObject);

	}

	@Override
	protected void logAndStreamStatusChanged() {
		// TODO Auto-generated method stub
		
	}

	
	
}

