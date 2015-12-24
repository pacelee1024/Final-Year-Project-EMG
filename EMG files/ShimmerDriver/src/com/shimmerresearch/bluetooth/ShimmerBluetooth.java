//Rev_1.9
/*
 * Copyright (c) 2010 - 2014, Shimmer Research, Ltd.
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
 * @author Jong Chern Lim, Ruaidhri Molloy
 * @date  September, 2014
 * 
 * Changes since 1.8
 * - set mInstructionStackLock in initialize(), this fix a bug when upon a ack timeout disconnect shimmer is unable to reconnect
 * - add mtimer cancel and purge to intialize for precaution
 * - added reset, to prevent API thinking it is the wrong fwidentifier (e.g. using btstream after logandstream) causing the get_status timer to be called
 * - added readBlinkLED to initializeShimmer3, remove mCurrentLEDStatus from startStreaming
 * - added a check for get_dir and get_status timeout, device won't disconnect if there is packet loss detected
 * 
 * Changes since since 1.7 
 * - updated logandstream support, now supports push button, start-stop streaming
 * 
 * Changes since 1.6
 * - updated to support LogAndStream
 * - updated checkBatt()
 * 
 * Changes since 1.5
 * - updated comments
 * - Baud rate setting support
 *
 * Changes since 1.4.04
 * - Reduce timeout for get_shimmer_version_command_new, to speed up connection for Shimmer2r 
 * - Move timeout response task to here, removed from Shimmer and ShimmerPCBT
 * - Added 
 *
 * Changes since 1.4.03
 * - support for Shimmer3 bridge amplifier, sensor conflict handling for Shimmer3
 * - Added isEXGUsingTestSignal24Configuration() isEXGUsingTestSignal16Configuration() isEXGUsingECG24Configuration() isEXGUsingECG16Configuration() isEXGUsingEMG24Configuration() isEXGUsingEMG16Configuration()
 * 
 * Changes since 1.4.02
 * - moved setting of writeexg setting to after the ack, otherwise readexg and writeexg in the instruction stack will yield wrong results
 * 
 *  Changes since 1.4.01
 *  - added exg set configuration to initialize shimmer3 exg from constructor
 * 
 *  Changes since 1.4
 *  - removed mSamplingRate decimal formatter, decimal formatter should be done on the UI
 *  - remove null characters from mListofInstructions, after a stop streaming command, this was causing a race condition error
 *  
 */



package com.shimmerresearch.bluetooth;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EmptyStackException;
import java.util.List;
import java.util.Locale;
import java.util.Stack;
import java.util.Timer;
import java.util.TimerTask;













import sun.rmi.runtime.Log;

import com.shimmerresearch.driver.Configuration;
import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.driver.ShimmerObject;

public abstract class ShimmerBluetooth extends ShimmerObject {
	
	//region --------- CLASS VARIABLES AND ABSTRACT METHODS ---------
	
	protected int mSetEnabledSensors = SENSOR_ACCEL;								// Only used during the initialization process, see initialize();
	// Constants that indicate the current connection state
	
	public static final int STATE_NONE = 0;       // The class is doing nothing
	public static final int STATE_CONNECTING = 1; // The class is now initiating an outgoing connection
	public static final int STATE_CONNECTED = 2;  // The class is now connected to a remote device
	protected boolean mInstructionStackLock = false;
	protected int mState;
	protected byte mCurrentCommand;	
	protected boolean mWaitForAck=false;                                          // This indicates whether the device is waiting for an acknowledge packet from the Shimmer Device  
	protected boolean mWaitForResponse=false; 									// This indicates whether the device is waiting for a response packet from the Shimmer Device 
	protected boolean mTransactionCompleted=true;									// Variable is used to ensure a command has finished execution prior to executing the next command (see initialize())
	protected IOThread mIOThread;
	protected boolean mContinousSync=false;                                       // This is to select whether to continuously check the data packets 
	protected boolean mSetupDevice=false;		
	protected Stack<Byte> byteStack = new Stack<Byte>();
	protected double mLowBattLimit=3.4;
	protected int numBytesToReadFromExpBoard=0;
	
	protected abstract void connect(String address,String bluetoothLibrary);
	protected abstract void dataHandler(ObjectCluster ojc);
	protected abstract boolean bytesToBeRead();
	protected abstract int availableBytes();
	
	protected abstract void writeBytes(byte[] data);
	protected abstract void stop();
	protected abstract void isNowStreaming();
	protected abstract void hasStopStreaming();
	protected abstract void sendStatusMsgPacketLossDetected();
	protected abstract void inquiryDone();
	protected abstract void sendStatusMSGtoUI(String msg);
	protected abstract void printLogDataForDebugging(String msg);
	protected abstract void isReadyForStreaming();
	protected abstract void connectionLost();
	protected abstract void setState(int state);
	protected abstract void logAndStreamStatusChanged();
	
	protected boolean mInitialized = false;
	protected abstract byte[] readBytes(int numberofBytes);
	protected abstract byte readByte();
	protected List<byte []> mListofInstructions = new  ArrayList<byte[]>();
	private final int ACK_TIMER_DURATION = 2; 									// Duration to wait for an ack packet (seconds)
	protected Timer mTimer;														// Timer variable used when waiting for an ack or response packet
	protected boolean mDummy=false;
	protected boolean mFirstTime=true;
	private byte mTempByteValue;												// A temporary variable used to store Byte value	
	protected int mTempIntValue;													// A temporary variable used to store Integer value, used mainly to store a value while waiting for an acknowledge packet (e.g. when writeGRange() is called, the range is stored temporarily and used to update GSRRange when the acknowledge packet is received.
	protected int tempEnabledSensors;												// This stores the enabled sensors
	private int mTempChipID;
	protected boolean mSync=true;													// Variable to keep track of sync
	protected boolean mSetupEXG = false;
	private byte[] cmdcalibrationParameters = new byte [22];  
	private int mReadStatusPeriod=5000;
	protected Timer mTimerToReadStatus;
	
	//endregion
	
	/**
	 * Provides an interface directly to the method BuildMSG. This can be used to implement algorithm/filters/etc. Two methods are provided, processdata to implement your methods, and InitializeProcessData which is called everytime you startstreaming, in the event you need to reinitialize your method/algorithm everytime a Shimmer starts streaming
	 *
	 */
	public interface DataProcessing {

		/**
		 * Initialise your method/algorithm here, this callback is called when startstreaming is called
		 */
		
		/** Initialise Process Data here. This is called whenever the startStreaming command is called and can be used to initialise algorithms
		 * 
		 */
		public void InitializeProcessData();
		
		/** Process data here, algorithms can access the object cluster built by the buildMsg method here
		 * @param ojc the objectCluster built by the buildMsg method
		 * @return the processed objectCluster
		 */
		public ObjectCluster ProcessData(ObjectCluster ojc);

	}
	DataProcessing mDataProcessing;

	
	//region --------- BLUETOOH STACK --------- 
	
	public class IOThread extends Thread {
		byte[] tb ={0};
		byte[] newPacket=new byte[mPacketSize+1];
		public boolean stop = false;
		public synchronized void run() {
			while (!stop) {
				/////////////////////////
				// is an instruction running ? if not proceed
				if (mInstructionStackLock==false){
					// check instruction stack, are there any other instructions left to be executed?
					if (!mListofInstructions.isEmpty()) {
						if (mListofInstructions.get(0)==null) {
							mListofInstructions.remove(0);
							String msg = "Null Removed ";
							printLogDataForDebugging(msg);
						}
					}
					if (!mListofInstructions.isEmpty()){

						byte[] insBytes = (byte[]) mListofInstructions.get(0);

						mInstructionStackLock=true;
						mCurrentCommand=insBytes[0];
						mWaitForAck=true;
						String msg = "Command Transmitted: " + Arrays.toString(insBytes);
						printLogDataForDebugging(msg);

						if(!mStreaming){
							while(availableBytes()>0){ //this is to clear the buffer 
								tb=readBytes(availableBytes());
							}
						}
						 

						writeBytes(insBytes);

						if (mCurrentCommand==STOP_STREAMING_COMMAND){
							mStreaming=false;
							mListofInstructions.removeAll(Collections.singleton(null));
						} else {
							if (mCurrentCommand==GET_FW_VERSION_COMMAND){
								startResponseTimer(ACK_TIMER_DURATION);
							} else if (mCurrentCommand==GET_SAMPLING_RATE_COMMAND){
								startResponseTimer(ACK_TIMER_DURATION);
							} else if (mCurrentCommand==GET_SHIMMER_VERSION_COMMAND_NEW){
								startResponseTimer(ACK_TIMER_DURATION);
							} else {
								startResponseTimer(ACK_TIMER_DURATION+10);
							}
						}
						mTransactionCompleted=false;
					}


				}
				
				
				if (mWaitForAck==true && mStreaming ==false) {

					if (bytesToBeRead()){
						tb=readBytes(1);
						String msg="";
						//	msg = "rxb resp : " + Arrays.toString(tb);
						//	printLogDataForDebugging(msg);

						if (mCurrentCommand==STOP_STREAMING_COMMAND) { //due to not receiving the ack from stop streaming command we will skip looking for it.
							mTimer.cancel();
							mTimer.purge();
							mStreaming=false;
							mTransactionCompleted=true;
							mWaitForAck=false;
							try {
								Thread.sleep(200);	// Wait to ensure that we dont missed any bytes which need to be cleared
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
							byteStack.clear();

							while(availableBytes()>0){ //this is to clear the buffer 

								tb=readBytes(availableBytes());

							}
							hasStopStreaming();					
							mListofInstructions.remove(0);
							mListofInstructions.removeAll(Collections.singleton(null));
							mInstructionStackLock=false;
						}

						if ((byte)tb[0]==ACK_COMMAND_PROCESSED)
						{	
							msg = "Ack Received for Command: " + Byte.toString(mCurrentCommand);
							printLogDataForDebugging(msg);
							if (mCurrentCommand==START_STREAMING_COMMAND || mCurrentCommand==START_SDBT_COMMAND) {
								mTimer.cancel();
								mTimer.purge();
								mStreaming=true;
								mTransactionCompleted=true;
								byteStack.clear();
								isNowStreaming();
								mWaitForAck=false;
								mListofInstructions.remove(0);
								mInstructionStackLock=false;
							}
							
							else if (mCurrentCommand==SET_SAMPLING_RATE_COMMAND) {
								mTimer.cancel(); //cancel the ack timer
								mTimer.purge();
								mTransactionCompleted=true;
								mWaitForAck=false;
								byte[] instruction=mListofInstructions.get(0);
								double tempdouble=-1;
								if (mShimmerVersion==SHIMMER_2 || mShimmerVersion==SHIMMER_2R){
									tempdouble=(double)1024/instruction[1];
								} else {
									tempdouble = 32768/(double)((int)(instruction[1] & 0xFF) + ((int)(instruction[2] & 0xFF) << 8));
								}
								mSamplingRate = tempdouble;
								mListofInstructions.remove(0);
								mInstructionStackLock=false;
								if (mShimmerVersion == SHIMMER_3){ // has to be here because to ensure the current exgregister settings have been read back
									//check sampling rate and adjust accordingly;
									/*if (mSamplingRate<=128){
										writeEXGRateSetting(1,0);
										writeEXGRateSetting(2,0);
									} else if (mSamplingRate<=256){
										writeEXGRateSetting(1,1);
										writeEXGRateSetting(2,1);
									}
									else if (mSamplingRate<=512){
										writeEXGRateSetting(1,2);
										writeEXGRateSetting(2,2);
									}*/
								}
								
							}
							else if (mCurrentCommand==SET_BUFFER_SIZE_COMMAND) {
								mTimer.cancel(); //cancel the ack timer
								mTimer.purge();
								mTransactionCompleted = true;
								mWaitForAck=false;
								mBufferSize=(int)((byte[])mListofInstructions.get(0))[1];
								mListofInstructions.remove(0);
								mInstructionStackLock=false;
							}
							else if (mCurrentCommand==INQUIRY_COMMAND) {
								mWaitForResponse=true;
								mWaitForAck=false;
								mListofInstructions.remove(0);
							}
							else if (mCurrentCommand==GET_LSM303DLHC_ACCEL_LPMODE_COMMAND) {
								mWaitForResponse=true;
								mWaitForAck=false;
								mListofInstructions.remove(0);
							}
							else if (mCurrentCommand==GET_LSM303DLHC_ACCEL_HRMODE_COMMAND) {
								mWaitForResponse=true;
								mWaitForAck=false;
								mListofInstructions.remove(0);
							}
							else if (mCurrentCommand==GET_BUFFER_SIZE_COMMAND) {
								mWaitForAck=false;
								mWaitForResponse=true;
								mListofInstructions.remove(0);
							}
							else if (mCurrentCommand==GET_BLINK_LED) {
								mWaitForAck=false;
								mWaitForResponse=true;
								mListofInstructions.remove(0);
							}
							else if (mCurrentCommand==GET_MAG_SAMPLING_RATE_COMMAND) {
								mWaitForAck=false;
								mWaitForResponse=true;
								mListofInstructions.remove(0);
							}
							else if (mCurrentCommand==GET_MAG_GAIN_COMMAND) {
								mWaitForAck=false;
								mWaitForResponse=true;
								mListofInstructions.remove(0);
							}
							else if (mCurrentCommand==GET_ACCEL_SENSITIVITY_COMMAND) {
								mWaitForAck=false;
								mWaitForResponse=true;
							}
							else if (mCurrentCommand==GET_MPU9150_GYRO_RANGE_COMMAND) {
								mWaitForAck=false;
								mWaitForResponse=true;
							}
							else if (mCurrentCommand==GET_GSR_RANGE_COMMAND) {
								mWaitForAck=false;
								mWaitForResponse=true;
								mListofInstructions.remove(0);
							}
							else if (mCurrentCommand==GET_FW_VERSION_COMMAND) {
								mWaitForResponse = true;
								mWaitForAck=false;
							}
							else if (mCurrentCommand==GET_ECG_CALIBRATION_COMMAND) {
								mWaitForResponse = true;
								mWaitForAck=false;
								mListofInstructions.remove(0);
							}
							else if (mCurrentCommand==GET_EMG_CALIBRATION_COMMAND) {
								mWaitForResponse = true;
								mWaitForAck=false;
								mListofInstructions.remove(0);
							}
							else if (mCurrentCommand==SET_BLINK_LED) {
								mCurrentLEDStatus=(int)((byte[])mListofInstructions.get(0))[1];
								mTransactionCompleted = true;
								//mWaitForAck=false;
								mTimer.cancel(); //cancel the ack timer
								mTimer.purge();
								mListofInstructions.remove(0);
								mInstructionStackLock=false;
							}
							else if (mCurrentCommand==SET_GSR_RANGE_COMMAND) {

								mTransactionCompleted = true;
								mWaitForAck=false;
								mTimer.cancel(); //cancel the ack timer
								mTimer.purge();
								mGSRRange=(int)((byte [])mListofInstructions.get(0))[1];
								mListofInstructions.remove(0);
								mInstructionStackLock=false;
							}
							else if (mCurrentCommand==GET_SAMPLING_RATE_COMMAND) {
								mWaitForResponse=true;
								mWaitForAck=false;

							}
							else if (mCurrentCommand==GET_CONFIG_BYTE0_COMMAND) {
								mWaitForResponse=true;
								mWaitForAck=false;
								mListofInstructions.remove(0);
							}
							else if (mCurrentCommand==SET_CONFIG_BYTE0_COMMAND) {
								mTimer.cancel(); //cancel the ack timer
								mTimer.purge();
								mConfigByte0=(int)((byte [])mListofInstructions.get(0))[1];
								mWaitForAck=false;
								mTransactionCompleted=true;
								mListofInstructions.remove(0);
								mInstructionStackLock=false;
							} else if (mCurrentCommand==SET_LSM303DLHC_ACCEL_LPMODE_COMMAND) {
								mTimer.cancel(); //cancel the ack timer
								mTimer.purge();
								mWaitForAck=false;
								mTransactionCompleted=true;
								mListofInstructions.remove(0);
								mInstructionStackLock=false;
							} else if (mCurrentCommand==SET_LSM303DLHC_ACCEL_HRMODE_COMMAND) {
								mTimer.cancel(); //cancel the ack timer
								mTimer.purge();
								mWaitForAck=false;
								mTransactionCompleted=true;
								mListofInstructions.remove(0);
								mInstructionStackLock=false;
							}
							else if (mCurrentCommand==SET_PMUX_COMMAND) {
								mTimer.cancel(); //cancel the ack timer
								mTimer.purge();
								if (((byte[])mListofInstructions.get(0))[1]==1) {
									mConfigByte0=(byte) ((byte) (mConfigByte0|64)&(0xFF)); 
								}
								else if (((byte[])mListofInstructions.get(0))[1]==0) {
									mConfigByte0=(byte) ((byte)(mConfigByte0 & 191)&(0xFF));
								}
								mTransactionCompleted=true;
								mWaitForAck=false;
								mListofInstructions.remove(0);
								mInstructionStackLock=false;
							}
							else if(mCurrentCommand==SET_BMP180_PRES_RESOLUTION_COMMAND){
								mTimer.cancel(); //cancel the ack timer
								mPressureResolution=(int)((byte [])mListofInstructions.get(0))[1];
								mTransactionCompleted=true;
								mWaitForAck=false;
								mListofInstructions.remove(0);
								mInstructionStackLock=false;
							}
							else if (mCurrentCommand==SET_GYRO_TEMP_VREF_COMMAND) {
								mTimer.cancel(); //cancel the ack timer
								mTimer.purge();
								mTransactionCompleted=true;
								mConfigByte0=mTempByteValue;
								mWaitForAck=false;
							}
							else if (mCurrentCommand==SET_5V_REGULATOR_COMMAND) {
								mTimer.cancel(); //cancel the ack timer
								mTimer.purge();
								if (((byte[])mListofInstructions.get(0))[1]==1) {
									mConfigByte0=(byte) (mConfigByte0|128); 
								}
								else if (((byte[])mListofInstructions.get(0))[1]==0) {
									mConfigByte0=(byte)(mConfigByte0 & 127);
								}
								mTransactionCompleted=true;
								mWaitForAck=false;
								mListofInstructions.remove(0);
								mInstructionStackLock=false;
							}
							else if (mCurrentCommand==SET_INTERNAL_EXP_POWER_ENABLE_COMMAND) {
								mTimer.cancel(); //cancel the ack timer
								mTimer.purge();
								if (((byte[])mListofInstructions.get(0))[1]==1) {
									mConfigByte0 = (mConfigByte0|16777216); 
									mInternalExpPower = 1;
								}
								else if (((byte[])mListofInstructions.get(0))[1]==0) {
									mConfigByte0 = mConfigByte0 & 4278190079l;
									mInternalExpPower = 0;
								}
								mTransactionCompleted=true;
								mWaitForAck=false;
								mListofInstructions.remove(0);
								mInstructionStackLock=false;
							}
							else if (mCurrentCommand==SET_ACCEL_SENSITIVITY_COMMAND) {
								mTimer.cancel(); //cancel the ack timer
								mTimer.purge();
								mAccelRange=(int)(((byte[])mListofInstructions.get(0))[1]);
								if (mDefaultCalibrationParametersAccel == true){
									if (mShimmerVersion != SHIMMER_3){
										if (getAccelRange()==0){
											SensitivityMatrixAccel = SensitivityMatrixAccel1p5gShimmer2; 
										} else if (getAccelRange()==1){
											SensitivityMatrixAccel = SensitivityMatrixAccel2gShimmer2; 
										} else if (getAccelRange()==2){
											SensitivityMatrixAccel = SensitivityMatrixAccel4gShimmer2; 
										} else if (getAccelRange()==3){
											SensitivityMatrixAccel = SensitivityMatrixAccel6gShimmer2; 
										}
									} else if(mShimmerVersion == SHIMMER_3){
										SensitivityMatrixAccel = SensitivityMatrixLowNoiseAccel2gShimmer3;
										AlignmentMatrixAccel = AlignmentMatrixLowNoiseAccelShimmer3;
										OffsetVectorAccel = OffsetVectorLowNoiseAccelShimmer3;
									}
								}

								if (mDefaultCalibrationParametersDigitalAccel){
									if (mShimmerVersion == SHIMMER_3){
										if (getAccelRange()==1){
											SensitivityMatrixAccel2 = SensitivityMatrixWideRangeAccel4gShimmer3;
											AlignmentMatrixAccel2 = AlignmentMatrixWideRangeAccelShimmer3;
											OffsetVectorAccel2 = OffsetVectorWideRangeAccelShimmer3;
										} else if (getAccelRange()==2){
											SensitivityMatrixAccel2 = SensitivityMatrixWideRangeAccel8gShimmer3;
											AlignmentMatrixAccel2 = AlignmentMatrixWideRangeAccelShimmer3;
											OffsetVectorAccel2 = OffsetVectorWideRangeAccelShimmer3;
										} else if (getAccelRange()==3){
											SensitivityMatrixAccel2 = SensitivityMatrixWideRangeAccel16gShimmer3;
											AlignmentMatrixAccel2 = AlignmentMatrixWideRangeAccelShimmer3;
											OffsetVectorAccel2 = OffsetVectorWideRangeAccelShimmer3;
										} else if (getAccelRange()==0){
											SensitivityMatrixAccel2 = SensitivityMatrixWideRangeAccel2gShimmer3;
											AlignmentMatrixAccel2 = AlignmentMatrixWideRangeAccelShimmer3;
											OffsetVectorAccel2 = OffsetVectorWideRangeAccelShimmer3;
										}
									}
								}
								mTransactionCompleted=true;
								mWaitForAck=false;
								mListofInstructions.remove(0);
								mInstructionStackLock=false;
							} 
							
							else if (mCurrentCommand==SET_ACCEL_CALIBRATION_COMMAND) {
								mTimer.cancel(); //cancel the ack timer
								mTimer.purge();
								retrievekinematiccalibrationparametersfrompacket(Arrays.copyOfRange(mListofInstructions.get(0), 1, mListofInstructions.get(0).length), ACCEL_CALIBRATION_RESPONSE);	
								mTransactionCompleted = true;
								mWaitForAck=false;
								mListofInstructions.remove(0);
								mInstructionStackLock=false;
								}
							else if (mCurrentCommand==SET_GYRO_CALIBRATION_COMMAND) {
								mTimer.cancel(); //cancel the ack timer
								mTimer.purge();	
								retrievekinematiccalibrationparametersfrompacket(Arrays.copyOfRange(mListofInstructions.get(0), 1, mListofInstructions.get(0).length), GYRO_CALIBRATION_RESPONSE);	
								mTransactionCompleted = true;
								mWaitForAck=false;
								mListofInstructions.remove(0);
								mInstructionStackLock=false;
							}
							else if (mCurrentCommand==SET_MAG_CALIBRATION_COMMAND) {
								mTimer.cancel(); //cancel the ack timer
								mTimer.purge();
								retrievekinematiccalibrationparametersfrompacket(Arrays.copyOfRange(mListofInstructions.get(0), 1, mListofInstructions.get(0).length), MAG_CALIBRATION_RESPONSE);	
								mTransactionCompleted = true;
								mWaitForAck=false;
								mListofInstructions.remove(0);
								mInstructionStackLock=false;
							}
							else if (mCurrentCommand==SET_LSM303DLHC_ACCEL_CALIBRATION_COMMAND) {
								mTimer.cancel(); //cancel the ack timer
								mTimer.purge();
								retrievekinematiccalibrationparametersfrompacket(Arrays.copyOfRange(mListofInstructions.get(0), 1, mListofInstructions.get(0).length), LSM303DLHC_ACCEL_CALIBRATION_RESPONSE);	
								mTransactionCompleted = true;
								mWaitForAck=false;
								mListofInstructions.remove(0);
								mInstructionStackLock=false;
							}
							
							else if (mCurrentCommand==SET_MPU9150_GYRO_RANGE_COMMAND) {
								mTimer.cancel(); //cancel the ack timer
								mTimer.purge();
								mGyroRange=(int)(((byte[])mListofInstructions.get(0))[1]);
								if (mDefaultCalibrationParametersGyro == true){
									if(mShimmerVersion == SHIMMER_3){
										AlignmentMatrixGyro = AlignmentMatrixGyroShimmer3;
										OffsetVectorGyro = OffsetVectorGyroShimmer3;
										if (mGyroRange==0){
											SensitivityMatrixGyro = SensitivityMatrixGyro250dpsShimmer3;

										} else if (mGyroRange==1){
											SensitivityMatrixGyro = SensitivityMatrixGyro500dpsShimmer3;

										} else if (mGyroRange==2){
											SensitivityMatrixGyro = SensitivityMatrixGyro1000dpsShimmer3;

										} else if (mGyroRange==3){
											SensitivityMatrixGyro = SensitivityMatrixGyro2000dpsShimmer3;

										}
									}
								}
								mTransactionCompleted=true;
								mWaitForAck=false;
								mListofInstructions.remove(0);
								mInstructionStackLock=false;
							} 
							else if (mCurrentCommand==SET_MAG_SAMPLING_RATE_COMMAND){
								mTimer.cancel(); //cancel the ack timer
								mTimer.purge();
								mTransactionCompleted = true;
								mMagSamplingRate = mTempIntValue;
								mWaitForAck = false;
								mListofInstructions.remove(0);
								mInstructionStackLock=false;
							} else if (mCurrentCommand==GET_ACCEL_SAMPLING_RATE_COMMAND){
								mWaitForAck=false;
								mWaitForResponse=true;
								mListofInstructions.remove(0);
							} else if (mCurrentCommand==SET_ACCEL_SAMPLING_RATE_COMMAND){
								mTimer.cancel(); //cancel the ack timer
								mTimer.purge();
								mTransactionCompleted = true;
								mAccelSamplingRate = mTempIntValue;
								mWaitForAck = false;
								mListofInstructions.remove(0);
								mInstructionStackLock=false;
							} else if (mCurrentCommand==SET_MPU9150_SAMPLING_RATE_COMMAND){
								mTimer.cancel(); //cancel the ack timer
								mTimer.purge();
								mTransactionCompleted = true;
								mMPU9150SamplingRate = mTempIntValue;
								mWaitForAck = false;
								mListofInstructions.remove(0);
								mInstructionStackLock=false;
							} else if (mCurrentCommand==SET_EXG_REGS_COMMAND){
								mTimer.cancel(); //cancel the ack timer
								mTimer.purge();
								byte[] bytearray = mListofInstructions.get(0);
								if (bytearray[1]==EXG_CHIP1){  //0 = CHIP 1
									System.arraycopy(bytearray, 4, mEXG1Register, 0, 10);
									mEXG1RateSetting = mEXG1Register[0] & 7;
									mEXG1CH1GainSetting = (mEXG1Register[3] >> 4) & 7;
									mEXG1CH1GainValue = convertEXGGainSettingToValue(mEXG1CH1GainSetting);
									mEXG1CH2GainSetting = (mEXG1Register[4] >> 4) & 7;
									mEXG1CH2GainValue = convertEXGGainSettingToValue(mEXG1CH2GainSetting);
									mRefenceElectrode = mEXG1Register[5] & 0x0f;
								
								} else if (bytearray[1]==EXG_CHIP2){ //1 = CHIP 2
									System.arraycopy(bytearray, 4, mEXG2Register, 0, 10);
									mEXG2RateSetting = mEXG2Register[0] & 7;
									mEXG2CH1GainSetting = (mEXG2Register[3] >> 4) & 7;
									mEXG2CH1GainValue = convertEXGGainSettingToValue(mEXG2CH1GainSetting);
									mEXG2CH2GainSetting = (mEXG2Register[4] >> 4) & 7;
									mEXG2CH2GainValue = convertEXGGainSettingToValue(mEXG2CH2GainSetting);
								}
								mTransactionCompleted = true;
								mWaitForAck = false;
								mListofInstructions.remove(0);
								mInstructionStackLock=false;
							} else if (mCurrentCommand==SET_SENSORS_COMMAND) {
								mTimer.cancel(); //cancel the ack timer
								mTimer.purge();
								mWaitForAck=false;
								mEnabledSensors=tempEnabledSensors;
								byteStack.clear(); // Always clear the packetStack after setting the sensors, this is to ensure a fresh start
								mTransactionCompleted=true;
								mListofInstructions.remove(0);
								mInstructionStackLock=false;
							}
							else if (mCurrentCommand==SET_MAG_GAIN_COMMAND){
								mTimer.cancel(); //cancel the ack timer
								mTimer.purge();
								mTransactionCompleted = true;
								mWaitForAck = false;
								mMagGain=(int)((byte [])mListofInstructions.get(0))[1];
								if (mDefaultCalibrationParametersMag == true){
									if(mShimmerVersion == SHIMMER_3){
										AlignmentMatrixMag = AlignmentMatrixMagShimmer3;
										OffsetVectorMag = OffsetVectorMagShimmer3;
										if (mMagGain==1){
											SensitivityMatrixMag = SensitivityMatrixMag1p3GaShimmer3;
										} else if (mMagGain==2){
											SensitivityMatrixMag = SensitivityMatrixMag1p9GaShimmer3;
										} else if (mMagGain==3){
											SensitivityMatrixMag = SensitivityMatrixMag2p5GaShimmer3;
										} else if (mMagGain==4){
											SensitivityMatrixMag = SensitivityMatrixMag4GaShimmer3;
										} else if (mMagGain==5){
											SensitivityMatrixMag = SensitivityMatrixMag4p7GaShimmer3;
										} else if (mMagGain==6){
											SensitivityMatrixMag = SensitivityMatrixMag5p6GaShimmer3;
										} else if (mMagGain==7){
											SensitivityMatrixMag = SensitivityMatrixMag8p1GaShimmer3;
										}
									}
								}
								mListofInstructions.remove(0);
								mInstructionStackLock=false;
							}
							else if (mCurrentCommand==GET_ACCEL_CALIBRATION_COMMAND || mCurrentCommand==GET_GYRO_CALIBRATION_COMMAND || mCurrentCommand==GET_MAG_CALIBRATION_COMMAND || mCurrentCommand==GET_ALL_CALIBRATION_COMMAND) {
								mWaitForResponse = true;
								mWaitForAck=false;
								mListofInstructions.remove(0);
							}
							else if (mCurrentCommand==GET_SHIMMER_VERSION_COMMAND_NEW) {
								mWaitForResponse = true;
								mWaitForAck=false;
								mListofInstructions.remove(0);
							}
							else if (mCurrentCommand==GET_BMP180_CALIBRATION_COEFFICIENTS_COMMAND) {
								mWaitForResponse = true;
								mWaitForAck=false;
								mListofInstructions.remove(0);
							} else if (mCurrentCommand==GET_SHIMMER_VERSION_COMMAND) {
								mWaitForResponse = true;
								mWaitForAck=false;
								mListofInstructions.remove(0);
							} else if (mCurrentCommand==GET_EXG_REGS_COMMAND){
								byte[] bytearray = mListofInstructions.get(0);
								mTempChipID = bytearray[1];
								mWaitForResponse = true;
								mWaitForAck=false;
								mListofInstructions.remove(0);
							}
							else if (mCurrentCommand==SET_ECG_CALIBRATION_COMMAND){
								//mGSRRange=mTempIntValue;
								mDefaultCalibrationParametersECG = false;
								OffsetECGLALL=(double)((((byte[])mListofInstructions.get(0))[0]&0xFF)<<8)+(((byte[])mListofInstructions.get(0))[1]&0xFF);
								GainECGLALL=(double)((((byte[])mListofInstructions.get(0))[2]&0xFF)<<8)+(((byte[])mListofInstructions.get(0))[3]&0xFF);
								OffsetECGRALL=(double)((((byte[])mListofInstructions.get(0))[4]&0xFF)<<8)+(((byte[])mListofInstructions.get(0))[5]&0xFF);
								GainECGRALL=(double)((((byte[])mListofInstructions.get(0))[6]&0xFF)<<8)+(((byte[])mListofInstructions.get(0))[7]&0xFF);
								mTimer.cancel(); //cancel the ack timer
								mTimer.purge();
								mTransactionCompleted = true;
								mWaitForAck=false;
								mListofInstructions.remove(0);
								mInstructionStackLock=false;
							}
							else if (mCurrentCommand==SET_EMG_CALIBRATION_COMMAND){
								//mGSRRange=mTempIntValue;
								mDefaultCalibrationParametersEMG = false;
								OffsetEMG=(double)((((byte[])mListofInstructions.get(0))[0]&0xFF)<<8)+(((byte[])mListofInstructions.get(0))[1]&0xFF);
								GainEMG=(double)((((byte[])mListofInstructions.get(0))[2]&0xFF)<<8)+(((byte[])mListofInstructions.get(0))[3]&0xFF);
								mTransactionCompleted = true;
								mWaitForAck=false;
								mTimer.cancel(); //cancel the ack timer
								mTimer.purge();
								mListofInstructions.remove(0);
								mInstructionStackLock=false;
							}
							else if (mCurrentCommand==TOGGLE_LED_COMMAND){
								//mGSRRange=mTempIntValue;
								mTransactionCompleted = true;
								mWaitForAck=false;
								mTimer.cancel(); //cancel the ack timer
								mTimer.purge();
								mListofInstructions.remove(0);
								mInstructionStackLock=false;
							}
							else if (mCurrentCommand==GET_BAUD_RATE_COMMAND) {
								mWaitForResponse = true;
								mWaitForAck=false;
								mListofInstructions.remove(0);
							}
							else if (mCurrentCommand==SET_BAUD_RATE_COMMAND) {
								mTransactionCompleted = true;
								mWaitForAck=false;
								mTimer.cancel(); //cancel the ack timer
								mTimer.purge();
								mBaudRate=(int)((byte [])mListofInstructions.get(0))[1];
								mListofInstructions.remove(0);
								mInstructionStackLock=false;
//								reconnect();
							}
							else if(mCurrentCommand==GET_DAUGHTER_CARD_ID_COMMAND){
								mWaitForResponse = true;
								mWaitForAck=false;
								mListofInstructions.remove(0);
							}
							else if(mCurrentCommand==GET_DIR_COMMAND){
								mWaitForResponse = true;
								mWaitForAck=false;
								mListofInstructions.remove(0);
							}
							else if(mCurrentCommand==GET_STATUS_COMMAND){
								mWaitForResponse = true;
								mWaitForAck=false;
								mListofInstructions.remove(0);
							}

						}


					}
				}
				else if (mWaitForResponse==true) {
					if (mFirstTime){
						while (availableBytes()!=0){
								int avaible = availableBytes();
								if (bytesToBeRead()){
									tb=readBytes(1);
									String msg = "First Time : " + Arrays.toString(tb);
									printLogDataForDebugging(msg);
								}
							
						}
						
					} else if (availableBytes()!=0){

						tb=readBytes(1);
						
						String msg="";
						//msg = "rxb : " + Arrays.toString(tb);
						//printLogDataForDebugging(msg);
						
						if (tb[0]==FW_VERSION_RESPONSE){
							mTimer.cancel(); //cancel the ack timer
							mTimer.purge();

							try {
								Thread.sleep(200);	// Wait to ensure the packet has been fully received
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
							byte[] bufferInquiry = new byte[6]; 
							bufferInquiry = readBytes(6);
							mFWIdentifier=(double)((bufferInquiry[1]&0xFF)<<8)+(double)(bufferInquiry[0]&0xFF);
//							mFWVersion=(double)((bufferInquiry[3]&0xFF)<<8)+(double)(bufferInquiry[2]&0xFF)+((double)((bufferInquiry[4]&0xFF))/10);
							mFWMajorVersion = (int)((bufferInquiry[3]&0xFF)<<8)+(int)(bufferInquiry[2]&0xFF);
							mFWMinorVersion = ((int)((bufferInquiry[4]&0xFF)));
							mFWInternal=(int)(bufferInquiry[5]&0xFF);
							
//							if (((double)((bufferInquiry[4]&0xFF))/10)==0){
//								mFWVersionFullName = "BtStream " + Double.toString(mFWVersion) + "."+ Integer.toString(mFWInternal);
//							} else {
//								mFWVersionFullName = "BtStream " + Double.toString(mFWVersion) + "."+ Integer.toString(mFWInternal);
//							}
							if(mFWIdentifier==1){ //BTStream
								if((mFWMajorVersion==0 && mFWMinorVersion==1) || (mFWMajorVersion==1 && mFWMinorVersion==2 && mShimmerVersion==SHIMMER_2R))
									mFWCode = 1;
								else if(mFWMajorVersion==0 && mFWMinorVersion==2)
									mFWCode = 2;
								else if(mFWMajorVersion==0 && mFWMinorVersion==3)
									mFWCode = 3;
								else if(mFWMajorVersion==0 && mFWMinorVersion==4)
									mFWCode = 4;
								else if(mFWMajorVersion==0 && mFWMinorVersion==5)
									mFWCode = 5;
								
								mFWVersionFullName = "BtStream " + mFWMajorVersion + "." + mFWMinorVersion + "."+ mFWInternal;
							}
							else if(mFWIdentifier==3){ //LogAndStream
								if(mFWMajorVersion==0 && mFWMinorVersion==1)
									mFWCode = 3;
								else if(mFWMajorVersion==0 && mFWMinorVersion==2)
									mFWCode = 4;
								else if(mFWMajorVersion==0 && mFWMinorVersion==3)
									mFWCode = 5;
								
								mFWVersionFullName = "LogAndStream " + mFWMajorVersion + "." + mFWMinorVersion + "."+ mFWInternal;
							}

							printLogDataForDebugging("FW Version Response Received. FW Code: " + mFWCode);
							msg = "FW Version Response Received: " +mFWVersionFullName;
							printLogDataForDebugging(msg);
							mListofInstructions.remove(0);
							mInstructionStackLock=false;
							mTransactionCompleted=true;
							if (mShimmerVersion == SHIMMER_2R){
								initializeShimmer2R();
							} else if (mShimmerVersion == SHIMMER_3) {
								initializeShimmer3();
							}
//							readShimmerVersion();
						} else if (tb[0]==BMP180_CALIBRATION_COEFFICIENTS_RESPONSE){
							mTimer.cancel(); //cancel the ack timer
							mTimer.purge();
							mWaitForResponse=false;
							mTransactionCompleted=true;
							
							//get pressure
							try {
								Thread.sleep(100);	// Due to the nature of the Bluetooth SPP stack a delay has been added to ensure the buffer is filled before it is read
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
							
							byte[] pressureResoRes = new byte[22]; 
						
							pressureResoRes = readBytes(22);
							mPressureCalRawParams = new byte[23];
							System.arraycopy(pressureResoRes, 0, mPressureCalRawParams, 1, 22);
							mPressureCalRawParams[0] = tb[0];
							retrievepressurecalibrationparametersfrompacket(pressureResoRes,tb[0]);
							msg = "BMP180 Response Received";
							printLogDataForDebugging(msg);
							mInstructionStackLock=false;
						} else if (tb[0]==INQUIRY_RESPONSE) {
							mTimer.cancel(); //cancel the ack timer
							mTimer.purge();
							try {
								Thread.sleep(500);	// Wait to ensure the packet has been fully received
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
							List<Byte> buffer = new  ArrayList<Byte>();
							if (!(mShimmerVersion==SHIMMER_3))
							{
								 for (int i = 0; i < 5; i++)
	                                {
	                                    // get Sampling rate, accel range, config setup byte0, num chans and buffer size
	                                    buffer.add(readByte());
	                                }
								 
	                                for (int i = 0; i < (int)buffer.get(3); i++)
	                                {
	                                    // read each channel type for the num channels
	                                	buffer.add(readByte());
	                                }
							}
							else
							{
								  for (int i = 0; i < 8; i++)
	                                {
	                                    // get Sampling rate, accel range, config setup byte0, num chans and buffer size
									  buffer.add(readByte());
	                                }
	                                for (int i = 0; i < (int)buffer.get(6); i++)
	                                {
	                                    // read each channel type for the num channels
	                                	buffer.add(readByte());
	                                }
							}
							byte[] bufferInquiry = new byte[buffer.size()];
							for (int i = 0; i < bufferInquiry.length; i++) {
								bufferInquiry[i] = (byte) buffer.get(i);
							}
								
							msg = "Inquiry Response Received: " + Arrays.toString(bufferInquiry);
							printLogDataForDebugging(msg);
							interpretInqResponse(bufferInquiry);
							inquiryDone();
							mWaitForResponse = false;
							mTransactionCompleted=true;
							mInstructionStackLock=false;
						} else if(tb[0] == GSR_RANGE_RESPONSE) {
							mTimer.cancel(); //cancel the ack timer
							mTimer.purge();
							mWaitForResponse=false;
							mTransactionCompleted=true;
							byte[] bufferGSRRange = readBytes(1); 
							mGSRRange=bufferGSRRange[0];
							msg = "GSR Range Response Received: " + Arrays.toString(bufferGSRRange);
							printLogDataForDebugging(msg);
							mInstructionStackLock=false;
						} else if(tb[0] == MAG_SAMPLING_RATE_RESPONSE) {
							mTimer.cancel(); //cancel the ack timer
							mTimer.purge();
							mWaitForResponse=false;
							mTransactionCompleted=true;
							byte[] bufferAns = readBytes(1); 
							mMagSamplingRate=bufferAns[0];
							msg = "Mag Sampling Rate Response Received: " + Arrays.toString(bufferAns);
							printLogDataForDebugging(msg);
							mInstructionStackLock=false;
						} else if(tb[0] == ACCEL_SAMPLING_RATE_RESPONSE) {
							mTimer.cancel(); //cancel the ack timer
							mTimer.purge();
							mWaitForResponse=false;
							mTransactionCompleted=true;
							byte[] bufferAns = readBytes(1); 
							mAccelSamplingRate=bufferAns[0];
							mInstructionStackLock=false;
						} else if(tb[0] == 	EXG_REGS_RESPONSE){
							mTimer.cancel(); //cancel the ack timer
							mTimer.purge();
							mWaitForResponse=false;
							mTransactionCompleted=true;
							try {
								Thread.sleep(300);	// Due to the nature of the Bluetooth SPP stack a delay has been added to ensure the buffer is filled before it is read
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
							byte[] bufferAns = readBytes(11);
							if (mTempChipID==0){
								System.arraycopy(bufferAns, 1, mEXG1Register, 0, 10);
								// retrieve the gain and rate from the the registers
								mEXG1RateSetting = mEXG1Register[0] & 7;
								mEXG1CH1GainSetting = (mEXG1Register[3] >> 4) & 7;
								mEXG1CH1GainValue = convertEXGGainSettingToValue(mEXG1CH1GainSetting);
								mEXG1CH2GainSetting = (mEXG1Register[4] >> 4) & 7;
								mEXG1CH2GainValue = convertEXGGainSettingToValue(mEXG1CH2GainSetting);
								mRefenceElectrode = mEXG1Register[5] & 0x0F;
								mLeadOffCurrentModeChip1 = mEXG1Register[2] & 1;
								mComparatorsChip1 = mEXG1Register[1] & 0x40;								
								mRLDSense = mEXG1Register[5] & 0x10;
								m2P1N1P = mEXG1Register[6] & 0x0f;
								mLeadOffDetectionCurrent = (mEXG1Register[2] >> 2) & 3;
								mLeadOffComparatorTreshold = (mEXG1Register[2] >> 5) & 7;
							} else if (mTempChipID==1){
								System.arraycopy(bufferAns, 1, mEXG2Register, 0, 10);						
								mEXG2RateSetting = mEXG2Register[0] & 7;
								mEXG2CH1GainSetting = (mEXG2Register[3] >> 4) & 7;
								mEXG2CH1GainValue = convertEXGGainSettingToValue(mEXG2CH1GainSetting);
								mEXG2CH2GainSetting = (mEXG2Register[4] >> 4) & 7;
								mEXG2CH2GainValue = convertEXGGainSettingToValue(mEXG2CH2GainSetting);
								mLeadOffCurrentModeChip2 = mEXG2Register[2] & 1;
								mComparatorsChip2 = mEXG2Register[1] & 0x40;
								m2P = mEXG2Register[6] & 0x0f;
							}
							if(mComparatorsChip1 == 0 && mComparatorsChip2 == 0 && m2P1N1P == 0 && m2P == 0){
								mLeadOffDetectionMode = 0; // Off
							}
							else if(mLeadOffCurrentModeChip1 == mLeadOffCurrentModeChip2 && mLeadOffCurrentModeChip1 == 0){
								mLeadOffDetectionMode = 1; // DC Current
							}
							else if(mLeadOffCurrentModeChip1 == mLeadOffCurrentModeChip2 && mLeadOffCurrentModeChip1 == 1){
								mLeadOffDetectionMode = 2; // AC Current. Not supported yet
							}
							mInstructionStackLock=false;
						} else if(tb[0] == MAG_GAIN_RESPONSE) {
							mTimer.cancel(); //cancel the ack timer
							mTimer.purge();
							mWaitForResponse=false;
							mTransactionCompleted=true;
							byte[] bufferAns = readBytes(1); 
							mMagGain=bufferAns[0];
							mInstructionStackLock=false;
						} else if(tb[0] == LSM303DLHC_ACCEL_HRMODE_RESPONSE) {
							mTimer.cancel(); //cancel the ack timer
							mTimer.purge();
							mWaitForResponse=false;
							mTransactionCompleted=true;
							byte[] bufferAns = readBytes(1);
							mInstructionStackLock=false;
						} else if(tb[0] == LSM303DLHC_ACCEL_LPMODE_RESPONSE) {
							mTimer.cancel(); //cancel the ack timer
							mTimer.purge();
							mWaitForResponse=false;
							mTransactionCompleted=true;
							byte[] bufferAns = readBytes(1);
							mInstructionStackLock=false;
						} else if(tb[0]==BUFFER_SIZE_RESPONSE) {
							mTimer.cancel(); //cancel the ack timer
							mTimer.purge();
							mWaitForResponse=false;
							mTransactionCompleted=true;
							byte[] byteled = readBytes(1);
							mBufferSize = byteled[0] & 0xFF;
							mInstructionStackLock=false;
						} else if(tb[0]==BLINK_LED_RESPONSE) {
							mTimer.cancel(); //cancel the ack timer
							mTimer.purge();
							mWaitForResponse=false;
							mTransactionCompleted=true;
							byte[] byteled = readBytes(1);
							mCurrentLEDStatus = byteled[0]&0xFF;
							mInstructionStackLock=false;
						} else if(tb[0]==ACCEL_SENSITIVITY_RESPONSE) {
							mTimer.cancel(); //cancel the ack timer
							mTimer.purge();
							mWaitForResponse=false;
							mTransactionCompleted=true;
							byte[] bufferAccelSensitivity = readBytes(1);
							mAccelRange=bufferAccelSensitivity[0];
							if (mDefaultCalibrationParametersAccel == true){
								if (mShimmerVersion != SHIMMER_3){
									if (getAccelRange()==0){
										SensitivityMatrixAccel = SensitivityMatrixAccel1p5gShimmer2; 
									} else if (getAccelRange()==1){
										SensitivityMatrixAccel = SensitivityMatrixAccel2gShimmer2; 
									} else if (getAccelRange()==2){
										SensitivityMatrixAccel = SensitivityMatrixAccel4gShimmer2; 
									} else if (getAccelRange()==3){
										SensitivityMatrixAccel = SensitivityMatrixAccel6gShimmer2; 
									}
								} else if(mShimmerVersion == SHIMMER_3){
									if (getAccelRange()==0){
										SensitivityMatrixAccel = SensitivityMatrixLowNoiseAccel2gShimmer3;
										AlignmentMatrixAccel = AlignmentMatrixLowNoiseAccelShimmer3;
										OffsetVectorAccel = OffsetVectorLowNoiseAccelShimmer3;
									} else if (getAccelRange()==1){
										SensitivityMatrixAccel = SensitivityMatrixWideRangeAccel4gShimmer3;
										AlignmentMatrixAccel = AlignmentMatrixWideRangeAccelShimmer3;
										OffsetVectorAccel = OffsetVectorWideRangeAccelShimmer3;
									} else if (getAccelRange()==2){
										SensitivityMatrixAccel = SensitivityMatrixWideRangeAccel8gShimmer3;
										AlignmentMatrixAccel = AlignmentMatrixWideRangeAccelShimmer3;
										OffsetVectorAccel = OffsetVectorWideRangeAccelShimmer3;
									} else if (getAccelRange()==3){
										SensitivityMatrixAccel = SensitivityMatrixWideRangeAccel16gShimmer3;
										AlignmentMatrixAccel = AlignmentMatrixWideRangeAccelShimmer3;
										OffsetVectorAccel = OffsetVectorWideRangeAccelShimmer3;
									}
								}
							}   
							mListofInstructions.remove(0);
							mInstructionStackLock=false;
						} else if(tb[0]==MPU9150_GYRO_RANGE_RESPONSE) {
							mTimer.cancel(); //cancel the ack timer
							mTimer.purge();
							mWaitForResponse=false;
							mTransactionCompleted=true;
							byte[] bufferGyroSensitivity = readBytes(1);
							mGyroRange=bufferGyroSensitivity[0];
							if (mDefaultCalibrationParametersGyro == true){
								if(mShimmerVersion == SHIMMER_3){
									AlignmentMatrixGyro = AlignmentMatrixGyroShimmer3;
									OffsetVectorGyro = OffsetVectorGyroShimmer3;
									if (mGyroRange==0){
										SensitivityMatrixGyro = SensitivityMatrixGyro250dpsShimmer3;

									} else if (mGyroRange==1){
										SensitivityMatrixGyro = SensitivityMatrixGyro500dpsShimmer3;

									} else if (mGyroRange==2){
										SensitivityMatrixGyro = SensitivityMatrixGyro1000dpsShimmer3;

									} else if (mGyroRange==3){
										SensitivityMatrixGyro = SensitivityMatrixGyro2000dpsShimmer3;
									}
								}
							}   
							mListofInstructions.remove(0);
							mInstructionStackLock=false;
						}else if (tb[0]==SAMPLING_RATE_RESPONSE) {
							mTimer.cancel(); //cancel the ack timer
							mTimer.purge();
							mWaitForResponse=false;
							if(mStreaming==false) {
								if (mShimmerVersion==SHIMMER_2R || mShimmerVersion==SHIMMER_2){    
									byte[] bufferSR = readBytes(1);
									if (mCurrentCommand==GET_SAMPLING_RATE_COMMAND) { // this is a double check, not necessary 
										double val=(double)(bufferSR[0] & (byte) ACK_COMMAND_PROCESSED);
										mSamplingRate=1024/val;
									}
								} else if (mShimmerVersion==SHIMMER_3){
									byte[] bufferSR = readBytes(2); //read the sampling rate
									mSamplingRate = 32768/(double)((int)(bufferSR[0] & 0xFF) + ((int)(bufferSR[1] & 0xFF) << 8));
								}
							}

							msg = "Sampling Rate Response Received: " + Double.toString(mSamplingRate);
							printLogDataForDebugging(msg);
							mTransactionCompleted=true;
							mListofInstructions.remove(0);
							mInstructionStackLock=false;
						} else if (tb[0]==ACCEL_CALIBRATION_RESPONSE ) {
							mTimer.cancel(); //cancel the ack timer
							mTimer.purge();
								try {
								Thread.sleep(100);	// Due to the nature of the Bluetooth SPP stack a delay has been added to ensure the buffer is filled before it is read
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
							mWaitForResponse=false;
							byte[] bufferCalibrationParameters = readBytes(21);
							
							mAccelCalRawParams = new byte[22];
							System.arraycopy(bufferCalibrationParameters, 0, mAccelCalRawParams, 1, 21);
							mAccelCalRawParams[0] = tb[0];
							
							int packetType=tb[0];
							retrievekinematiccalibrationparametersfrompacket(bufferCalibrationParameters, packetType);
							msg = "Accel Calibration Response Received";
							printLogDataForDebugging(msg);
							mTransactionCompleted=true;
							mInstructionStackLock=false;
						}  else if (tb[0]==ALL_CALIBRATION_RESPONSE ) {
							mTimer.cancel(); //cancel the ack timer
							mTimer.purge();
							mWaitForResponse=false;
					
							try {
								Thread.sleep(100);	// Due to the nature of the Bluetooth SPP stack a delay has been added to ensure the buffer is filled before it is read
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
							if (mShimmerVersion != SHIMMER_3){
								byte[] bufferCalibrationParameters = readBytes(21);
								mAccelCalRawParams = new byte[22];
								System.arraycopy(bufferCalibrationParameters, 0, mAccelCalRawParams, 1, 21);
								mAccelCalRawParams[0] = ACCEL_CALIBRATION_RESPONSE;
								
								retrievekinematiccalibrationparametersfrompacket(bufferCalibrationParameters, ACCEL_CALIBRATION_RESPONSE);

								//get gyro
								try {
									Thread.sleep(100);	// Due to the nature of the Bluetooth SPP stack a delay has been added to ensure the buffer is filled before it is read
								} catch (InterruptedException e) {
									e.printStackTrace();
								}
								bufferCalibrationParameters = readBytes(21);
								mGyroCalRawParams = new byte[22];
								System.arraycopy(bufferCalibrationParameters, 0, mGyroCalRawParams, 1, 21);
								mGyroCalRawParams[0] = GYRO_CALIBRATION_RESPONSE;
								retrievekinematiccalibrationparametersfrompacket(bufferCalibrationParameters, GYRO_CALIBRATION_RESPONSE);

								//get mag
								try {
									Thread.sleep(100);	// Due to the nature of the Bluetooth SPP stack a delay has been added to ensure the buffer is filled before it is read
								} catch (InterruptedException e) {
									e.printStackTrace();
								}
								bufferCalibrationParameters = readBytes(21);
								mMagCalRawParams = new byte[22];
								System.arraycopy(bufferCalibrationParameters, 0, mMagCalRawParams, 1, 21);
								mMagCalRawParams[0] = MAG_CALIBRATION_RESPONSE;
								retrievekinematiccalibrationparametersfrompacket(bufferCalibrationParameters, MAG_CALIBRATION_RESPONSE);

								try {
									Thread.sleep(100);	// Due to the nature of the Bluetooth SPP stack a delay has been added to ensure the buffer is filled before it is read
								} catch (InterruptedException e) {
									e.printStackTrace();
								}
								
								bufferCalibrationParameters = readBytes(4); 
								mEMGCalRawParams = new byte[5];
								System.arraycopy(bufferCalibrationParameters, 0, mEMGCalRawParams, 1, 4);
								mEMGCalRawParams[0] = EMG_CALIBRATION_RESPONSE;
								retrievebiophysicalcalibrationparametersfrompacket( bufferCalibrationParameters,EMG_CALIBRATION_RESPONSE);
								
								bufferCalibrationParameters = readBytes(8);
								
								mECGCalRawParams = new byte[9];
								System.arraycopy(bufferCalibrationParameters, 0, mECGCalRawParams, 1, 8);
								mECGCalRawParams[0] = ECG_CALIBRATION_RESPONSE;
								retrievebiophysicalcalibrationparametersfrompacket( bufferCalibrationParameters,ECG_CALIBRATION_RESPONSE);
								
								mTransactionCompleted=true;
								mInstructionStackLock=false;

							} else {


								byte[] bufferCalibrationParameters =readBytes(21); 
								
								mAccelCalRawParams = new byte[22];
								System.arraycopy(bufferCalibrationParameters, 0, mAccelCalRawParams, 1, 21);
								mAccelCalRawParams[0] = ACCEL_CALIBRATION_RESPONSE;
								
								retrievekinematiccalibrationparametersfrompacket(bufferCalibrationParameters, ACCEL_CALIBRATION_RESPONSE);

								//get gyro
								try {
									Thread.sleep(100);	// Due to the nature of the Bluetooth SPP stack a delay has been added to ensure the buffer is filled before it is read
								} catch (InterruptedException e) {
									e.printStackTrace();
								}
								bufferCalibrationParameters = readBytes(21); 
								
								mGyroCalRawParams = new byte[22];
								System.arraycopy(bufferCalibrationParameters, 0, mGyroCalRawParams, 1, 21);
								mGyroCalRawParams[0] = GYRO_CALIBRATION_RESPONSE;
								
								retrievekinematiccalibrationparametersfrompacket(bufferCalibrationParameters, GYRO_CALIBRATION_RESPONSE);

								//get mag
								try {
									Thread.sleep(100);	// Due to the nature of the Bluetooth SPP stack a delay has been added to ensure the buffer is filled before it is read
								} catch (InterruptedException e) {
									e.printStackTrace();
								}
								bufferCalibrationParameters = readBytes(21); 
								
								mMagCalRawParams = new byte[22];
								System.arraycopy(bufferCalibrationParameters, 0, mMagCalRawParams, 1, 21);
								mMagCalRawParams[0] = MAG_CALIBRATION_RESPONSE;
								
								retrievekinematiccalibrationparametersfrompacket(bufferCalibrationParameters, MAG_CALIBRATION_RESPONSE);

								//second accel cal params
								try {
									Thread.sleep(100);	// Due to the nature of the Bluetooth SPP stack a delay has been added to ensure the buffer is filled before it is read
								} catch (InterruptedException e) {
									e.printStackTrace();
								}
								bufferCalibrationParameters = readBytes(21);
								
								mDigiAccelCalRawParams = new byte[22];
								System.arraycopy(bufferCalibrationParameters, 0, mDigiAccelCalRawParams, 1, 21);
								mDigiAccelCalRawParams[0] = LSM303DLHC_ACCEL_CALIBRATION_RESPONSE;
								msg = "All Calibration Response Received";
								printLogDataForDebugging(msg);
								retrievekinematiccalibrationparametersfrompacket(bufferCalibrationParameters, LSM303DLHC_ACCEL_CALIBRATION_RESPONSE);
								mTransactionCompleted=true;
								mInstructionStackLock=false;

							}
						} else if (tb[0]==GYRO_CALIBRATION_RESPONSE) {
							mTimer.cancel(); //cancel the ack timer
							mTimer.purge();
							try {
								Thread.sleep(100);	// Due to the nature of the Bluetooth SPP stack a delay has been added to ensure the buffer is filled before it is read
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
							mWaitForResponse=false;
							byte[] bufferCalibrationParameters = readBytes(21);
							mGyroCalRawParams = new byte[22];
							System.arraycopy(bufferCalibrationParameters, 0, mGyroCalRawParams, 1, 21);
							mGyroCalRawParams[0] = tb[0];
							
							int packetType=tb[0];
							retrievekinematiccalibrationparametersfrompacket(bufferCalibrationParameters, packetType);
							msg = "Gyro Calibration Response Received";
							printLogDataForDebugging(msg);
							mTransactionCompleted=true;
							mInstructionStackLock=false;
						} else if (tb[0]==MAG_CALIBRATION_RESPONSE ) {
							mTimer.cancel(); //cancel the ack timer
							mTimer.purge();
							mWaitForResponse=false;
							try {
								Thread.sleep(100);	// Due to the nature of the Bluetooth SPP stack a delay has been added to ensure the buffer is filled before it is read
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
							byte[] bufferCalibrationParameters = readBytes(21);
							mMagCalRawParams = new byte[22];
							System.arraycopy(bufferCalibrationParameters, 0, mMagCalRawParams, 1, 21);
							mMagCalRawParams[0] = tb[0];
							int packetType=tb[0];
							retrievekinematiccalibrationparametersfrompacket(bufferCalibrationParameters, packetType);
							msg = "Mag Calibration Response Received";
							printLogDataForDebugging(msg);
							mTransactionCompleted=true;
							mInstructionStackLock=false;
						} else if(tb[0]==CONFIG_BYTE0_RESPONSE) {
							mTimer.cancel(); //cancel the ack timer
							mTimer.purge();
							
							if (mShimmerVersion==SHIMMER_2R || mShimmerVersion==SHIMMER_2){    
								byte[] bufferConfigByte0 = readBytes(1);
								mConfigByte0 = bufferConfigByte0[0] & 0xFF;
							} else {
								byte[] bufferConfigByte0 = readBytes(4);
								mConfigByte0 = ((long)(bufferConfigByte0[0] & 0xFF) +((long)(bufferConfigByte0[1] & 0xFF) << 8)+((long)(bufferConfigByte0[2] & 0xFF) << 16) +((long)(bufferConfigByte0[3] & 0xFF) << 24));
							}
							msg = "ConfigByte0 response received Response Received";
							printLogDataForDebugging(msg);
							mTransactionCompleted=true;
							mInstructionStackLock=false;
						} else if(tb[0]==GET_SHIMMER_VERSION_RESPONSE) {
							mTimer.cancel(); //cancel the ack timer
							mTimer.purge();
							try {
								Thread.sleep(100);	// Due to the nature of the Bluetooth SPP stack a delay has been added to ensure the buffer is filled before it is read
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
							byte[] bufferShimmerVersion = new byte[1]; 
							bufferShimmerVersion = readBytes(1);
							mShimmerVersion=(int)bufferShimmerVersion[0];
							mTransactionCompleted=true;
							mInstructionStackLock=false;
//							if (mShimmerVersion == SHIMMER_2R){
//								initializeShimmer2R();
//							} else if (mShimmerVersion == SHIMMER_3) {
//								initializeShimmer3();
//							}
							msg = "Shimmer Version (HW) Response Received: " + Arrays.toString(bufferShimmerVersion);
							printLogDataForDebugging(msg);
							readFWVersion();
						} else if (tb[0]==ECG_CALIBRATION_RESPONSE){
							mTimer.cancel(); //cancel the ack timer
							mTimer.purge();
							try {
								Thread.sleep(100);	// Due to the nature of the Bluetooth SPP stack a delay has been added to ensure the buffer is filled before it is read
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
							byte[] bufferCalibrationParameters = new byte[8]; 
							bufferCalibrationParameters = readBytes(4);
															
							mECGCalRawParams = new byte[9];
							System.arraycopy(bufferCalibrationParameters, 0, mECGCalRawParams, 1, 8);
							mECGCalRawParams[0] = ECG_CALIBRATION_RESPONSE;
							//get ecg 
							retrievebiophysicalcalibrationparametersfrompacket( bufferCalibrationParameters,ECG_CALIBRATION_RESPONSE);
							msg = "ECG Calibration Response Received";
							printLogDataForDebugging(msg);
							mTransactionCompleted=true;
							mInstructionStackLock=false;
						} else if (tb[0]==EMG_CALIBRATION_RESPONSE){
							mTimer.cancel(); //cancel the ack timer
							mTimer.purge();
							try {
								Thread.sleep(100);	// Due to the nature of the Bluetooth SPP stack a delay has been added to ensure the buffer is filled before it is read
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
							byte[] bufferCalibrationParameters = new byte[4]; 
							bufferCalibrationParameters = readBytes(4);
							
							mEMGCalRawParams = new byte[5];
							System.arraycopy(bufferCalibrationParameters, 0, mEMGCalRawParams, 1, 4);
							mEMGCalRawParams[0] = EMG_CALIBRATION_RESPONSE;
							//get EMG
							msg = "EMG Calibration Response Received";
							printLogDataForDebugging(msg);
							retrievebiophysicalcalibrationparametersfrompacket( bufferCalibrationParameters,EMG_CALIBRATION_RESPONSE);
							mTransactionCompleted=true;
							mInstructionStackLock=false;
						}
						else if(tb[0] == BAUD_RATE_RESPONSE) {
							mTimer.cancel(); //cancel the ack timer
							mTimer.purge();
							mWaitForResponse=false;
							mTransactionCompleted=true;
							byte[] bufferBaud = readBytes(1); 
							printLogDataForDebugging(msg);
							mBaudRate=bufferBaud[0] & 0xFF;
							mInstructionStackLock=false;
						}
						else if(tb[0] == DAUGHTER_CARD_ID_RESPONSE) {
							mTimer.cancel(); //cancel the ack timer
							mTimer.purge();
							mWaitForResponse=false;
							mTransactionCompleted=true;
							printLogDataForDebugging(msg);
							mExpBoardArray = readBytes(numBytesToReadFromExpBoard+1);
							mInstructionStackLock=false;
						}
						else if(tb[0] == INSTREAM_CMD_RESPONSE) {
							mTimer.cancel(); //cancel the ack timer
							mTimer.purge();
							mWaitForResponse=false;
							mTransactionCompleted=true;
							printLogDataForDebugging(msg);
							byte[] bufferLogCommandType = new byte[2];
							bufferLogCommandType =	readBytes(2);
							System.out.println("Instrucction received (113 = STATUS_RESPONSE) = "+bufferLogCommandType[0]);
							if(bufferLogCommandType[0]==DIR_RESPONSE){
								mDirectoryNameLenght = bufferLogCommandType[1];
								byte[] bufferDirectoryName = new byte[mDirectoryNameLenght];
								bufferDirectoryName = readBytes(mDirectoryNameLenght);
								String tempDirectory = new String(bufferDirectoryName);
								mDirectoryName = tempDirectory;
								System.out.println("Directory Name = "+ mDirectoryName);
							}
							else if(bufferLogCommandType[0]==STATUS_RESPONSE){
								int sensing = bufferLogCommandType[1] & 2;
								if(sensing==2)
									mSensingStatus = true;
								else
									mSensingStatus = false;
								
								int docked = bufferLogCommandType[1] & 1;
								if(docked==1)
									mDockedStatus = true;
								else
									mDockedStatus = false;
								
								System.out.println("Sensing = "+sensing);
								System.out.println("Sensing status = "+mSensingStatus);
								System.out.println("Docked = "+docked);
								System.out.println("Docked status = "+mDockedStatus);
								
							}
							mInstructionStackLock=false;
						}
					}
				} if (mWaitForAck==false && mWaitForResponse == false && mStreaming ==false && availableBytes()!=0 && mFWIdentifier==3) {
					tb=readBytes(1);
					if(tb[0]==ACK_COMMAND_PROCESSED){
						System.out.println("ACK RECEIVED , Connected State!!");
						tb = readBytes(1);
						if (tb[0]==ACK_COMMAND_PROCESSED){
							tb = readBytes(1);
						}
						if(tb[0]==INSTREAM_CMD_RESPONSE){
							System.out.println("INS CMD RESP");
							byte[] command = readBytes(2);
							if(command[0]==DIR_RESPONSE){
								mDirectoryNameLenght = command[1];
								byte[] bufferDirectoryName = new byte[mDirectoryNameLenght];
								bufferDirectoryName = readBytes(mDirectoryNameLenght);
								String tempDirectory = new String(bufferDirectoryName);
								mDirectoryName = tempDirectory;

								System.out.println("DIR RESP : " + mDirectoryName);
							}
							else if(command[0]==STATUS_RESPONSE){
								int sensing = command[1] & 2;
								if(sensing==2)
									mSensingStatus = true;
								else
									mSensingStatus = false;

								int docked = command[1] & 1;
								if(docked==1)
									mDockedStatus = true;
								else
									mDockedStatus = false;

								if (mSensingStatus){
									//flush all the bytes
									while(availableBytes()!=0){
										System.out.println("Throwing away = "+readBytes(1));
									}
									startStreaming();
								}
								
								logAndStreamStatusChanged();
								
								System.out.println("Sensing = "+sensing);
								System.out.println("Sensing status = "+mSensingStatus);
								System.out.println("Docked = "+docked);
								System.out.println("Docked status = "+mDockedStatus);
							}
						}
					}
					
					while(availableBytes()!=0){
						System.out.println("Throwing away = "+readBytes(1));
					}
				}
				
				
				if (mStreaming==true) {
					tb = readBytes(1);

					//Log.d(mClassName,"Incoming Byte: " + Byte.toString(tb[0])); // can be commented out to watch the incoming bytes
					if (mSync==true) {        //if the stack is full
						if (mWaitForAck==true && (byte)tb[0]==ACK_COMMAND_PROCESSED && byteStack.size()==mPacketSize+1){ //this is to handle acks during mid stream, acks only are received between packets.
							if (mCurrentCommand==SET_BLINK_LED){
								mWaitForAck=false;
								mTransactionCompleted = true;   
								mTimer.cancel(); //cancel the ack timer
								mTimer.purge();
								mCurrentLEDStatus=(int)((byte[])mListofInstructions.get(0))[1];
								mListofInstructions.remove(0);
								mInstructionStackLock=false;
								System.out.println("LED COMMAND ACK RECEIVED");
							} else if (mFWIdentifier == 3 ){ //this is for logandstream stupport, command is trasmitted and ack received
								System.out.println("COMMAND TXed and ACK RECEIVED IN STREAM");
								tb = readBytes(1);
								if(tb[0]==INSTREAM_CMD_RESPONSE){
									System.out.println("INS CMD RESP");
									byte[] command = readBytes(2);
									if(command[0]==DIR_RESPONSE){
										mDirectoryNameLenght = command[1];
										byte[] bufferDirectoryName = new byte[mDirectoryNameLenght];
										bufferDirectoryName = readBytes(mDirectoryNameLenght);
										String tempDirectory = new String(bufferDirectoryName);
										mDirectoryName = tempDirectory;

										System.out.println("DIR RESP : " + mDirectoryName);
									}
									else if(command[0]==STATUS_RESPONSE){
										int sensing = command[1] & 2;
										if(sensing==2)
											mSensingStatus = true;
										else
											mSensingStatus = false;

										int docked = command[1] & 1;
										if(docked==1)
											mDockedStatus = true;
										else
											mDockedStatus = false;

										
										System.out.println("Sensing = "+sensing);
										System.out.println("Sensing status = "+mSensingStatus);
										System.out.println("Docked = "+docked);
										System.out.println("Docked status = "+mDockedStatus);
									}
									
									mWaitForAck=false;
									mTransactionCompleted = true;   
									mTimer.cancel(); //cancel the ack timer
									mTimer.purge();
									mListofInstructions.remove(0);
									mInstructionStackLock=false;
									
								}


							}
						} else { // the first time you start streaming it will go through this piece of code to make sure the data streaming is alligned/sync
							boolean inStream = false;
							if (byteStack.size()==mPacketSize+1){
								if (tb[0]==DATA_PACKET && byteStack.firstElement()==DATA_PACKET) { //check for the starting zero of the packet, and the starting zero of the subsequent packet, this causes a delay equivalent to the transmission duration between two packets
									newPacket=convertstacktobytearray(byteStack,mPacketSize);
									ObjectCluster objectCluster=new ObjectCluster(mMyName,getBluetoothAddress());
									objectCluster=(ObjectCluster) buildMsg(newPacket, objectCluster);
									if (mDataProcessing!=null){
										objectCluster = mDataProcessing.ProcessData(objectCluster);
									}
									//printtofile(newmsg.UncalibratedData);
									dataHandler(objectCluster);
									
									byteStack.clear();
									if (mContinousSync==false) {         //disable continuous synchronizing 
										mSync=false;
									}
								} 
								
								else if(tb[0]==ACK_COMMAND_PROCESSED && mFWIdentifier==3){ // this is for LogandStream support if the device is docked/undocked
									System.out.println("ACK RECEIVED");
									tb = readBytes(1);
									if (tb[0]==ACK_COMMAND_PROCESSED){
										tb = readBytes(1);
									}
									if(tb[0]==INSTREAM_CMD_RESPONSE){
										System.out.println("INS CMD RESP");
										inStream = true;
										byte[] command = readBytes(2);
										if(command[0]==DIR_RESPONSE){
											mDirectoryNameLenght = command[1];
											byte[] bufferDirectoryName = new byte[mDirectoryNameLenght];
											bufferDirectoryName = readBytes(mDirectoryNameLenght);
											String tempDirectory = new String(bufferDirectoryName);
											mDirectoryName = tempDirectory;

											System.out.println("DIR RESP : " + mDirectoryName);
										}
										else if(command[0]==STATUS_RESPONSE){
											int sensing = command[1] & 2;
											if(sensing==2)
												mSensingStatus = true;
											else
												mSensingStatus = false;

											int docked = command[1] & 1;
											if(docked==1)
												mDockedStatus = true;
											else
												mDockedStatus = false;

											if (!mSensingStatus){
												mStreaming=false;
												setState(ShimmerBluetooth.STATE_CONNECTED);
//												isReadyForStreaming();
												hasStopStreaming();
												if(mTimerToReadStatus==null){ 
													mTimerToReadStatus = new Timer();
													mTimerToReadStatus.schedule(new readStatusTask(), mReadStatusPeriod, mReadStatusPeriod);
												}
												//flush all the bytes
												while(availableBytes()!=0){
													System.out.println("Throwing away = "+readBytes(1));
												}
											}
											
											logAndStreamStatusChanged();
											
											System.out.println("Sensing = "+sensing);
											System.out.println("Sensing status = "+mSensingStatus);
											System.out.println("Docked = "+docked);
											System.out.println("Docked status = "+mDockedStatus);
										}
									}
								}
							}
							/*if (mStreaming==true && mWaitForAck==true && (byte)tb[0]==ACK_COMMAND_PROCESSED && (packetStack.size()==0)){ //this is to handle acks during mid stream, acks only are received between packets.
                        		Log.d("ShimmerCMD","LED_BLINK_ACK_DETECTED");
                        		mWaitForAck=false;
                        		mCurrentLEDStatus=mTempIntValue;
                    		    mTransactionCompleted = true;
                        	} */
							if(!inStream)
							{
								byteStack.push((tb[0])); //push new sensor data into the stack
							}
							if (byteStack.size()>mPacketSize+1) { //if the stack has reached the packet size remove an element from the stack
								System.out.println("Throw Bytes "+ byteStack.get(0));
								byteStack.removeElementAt(0);
								
							}
						}
					} else if (mSync==false){ //shimmershimmer
						if (mWaitForAck==true && (byte)tb[0]==ACK_COMMAND_PROCESSED && byteStack.size()==0){ //this is to handle acks during mid stream, acks only are received between packets.
							if (mCurrentCommand==SET_BLINK_LED){
							
								mWaitForAck=false;
								mTransactionCompleted = true;   
								mTimer.cancel(); //cancel the ack timer
								mTimer.purge();
								mCurrentLEDStatus=(int)((byte[])mListofInstructions.get(0))[1];
								mListofInstructions.remove(0);
								mInstructionStackLock=false;
								System.out.println("LED COMMAND ACK RECEIVED");
							} else if (mFWIdentifier ==3 ){ //ack received for LogandStream commands

								System.out.println("COMMAND TXed and ACK RECEIVED IN STREAM");
								tb = readBytes(1);
								if(tb[0]==INSTREAM_CMD_RESPONSE){
									System.out.println("INS CMD RESP");
									byte[] command = readBytes(2);
									if(command[0]==DIR_RESPONSE){
										mDirectoryNameLenght = command[1];
										byte[] bufferDirectoryName = new byte[mDirectoryNameLenght];
										bufferDirectoryName = readBytes(mDirectoryNameLenght);
										String tempDirectory = new String(bufferDirectoryName);
										mDirectoryName = tempDirectory;

										System.out.println("DIR RESP : " + mDirectoryName);
									}
									else if(command[0]==STATUS_RESPONSE){
										int sensing = command[1] & 2;
										if(sensing==2)
											mSensingStatus = true;
										else
											mSensingStatus = false;

										int docked = command[1] & 1;
										if(docked==1)
											mDockedStatus = true;
										else
											mDockedStatus = false;

										System.out.println("Sensing = "+sensing);
										System.out.println("Sensing status = "+mSensingStatus);
										System.out.println("Docked = "+docked);
										System.out.println("Docked status = "+mDockedStatus);
									}
									
									mWaitForAck=false;
									mTransactionCompleted = true;   
									mTimer.cancel(); //cancel the ack timer
									mTimer.purge();
									mListofInstructions.remove(0);
									mInstructionStackLock=false;
									
								}								
							}
						} else {
							
							byteStack.push((tb[0])); //push new sensor data into the stack
							boolean instream = false;
							
							//LogAndStream in stream ack command , without sending a command (e.g. dock/undock)
							if( (byteStack.size()==1 && tb[0]==ACK_COMMAND_PROCESSED && mFWIdentifier == 3) ){
								System.out.println("ACK RECEIVED");
								tb = readBytes(1);
								if (tb[0]==ACK_COMMAND_PROCESSED){
									tb = readBytes(1);
								}
								if(tb[0]==INSTREAM_CMD_RESPONSE){
									System.out.println("INS CMD RESP");
									byte[] command = readBytes(2);
									if(command[0]==DIR_RESPONSE){
										mDirectoryNameLenght = command[1];
										byte[] bufferDirectoryName = new byte[mDirectoryNameLenght];
										bufferDirectoryName = readBytes(mDirectoryNameLenght);
										String tempDirectory = new String(bufferDirectoryName);
										mDirectoryName = tempDirectory;

										System.out.println("DIR RESP : " + mDirectoryName);
									}
									else if(command[0]==STATUS_RESPONSE){
										int sensing = command[1] & 2;
										if(sensing==2)
											mSensingStatus = true;
										else
											mSensingStatus = false;

										int docked = command[1] & 1;
										if(docked==1)
											mDockedStatus = true;
										else
											mDockedStatus = false;

										if (!mSensingStatus){
											mStreaming=false;
											setState(ShimmerBluetooth.STATE_CONNECTED);
//											isReadyForStreaming();
											hasStopStreaming();
											if(mTimerToReadStatus==null){ 
												mTimerToReadStatus = new Timer();
												mTimerToReadStatus.schedule(new readStatusTask(), mReadStatusPeriod, mReadStatusPeriod);
											}
											//flush all the bytes
											while(availableBytes()!=0){
												System.out.println("Throwing away = "+readBytes(1));
											}

										}
										
										logAndStreamStatusChanged();
										
										System.out.println("Sensing = "+sensing);
										System.out.println("Sensing status = "+mSensingStatus);
										System.out.println("Docked = "+docked);
										System.out.println("Docked status = "+mDockedStatus);
										
									}
								}
								instream = true;
								byteStack.pop(); //remove the last element, which is the INSTREAM_COMMAND
							}
							if (!instream){
								if(byteStack.firstElement()==DATA_PACKET && (byteStack.size()==mPacketSize+1)) {         //only used when continous sync is disabled
									newPacket=convertstacktobytearray(byteStack,mPacketSize);
									ObjectCluster objectCluster=new ObjectCluster(mMyName,getBluetoothAddress());
									objectCluster=(ObjectCluster) buildMsg(newPacket, objectCluster);
									if (mDataProcessing!=null){
										objectCluster = mDataProcessing.ProcessData(objectCluster);
									}
									dataHandler(objectCluster);
									byteStack.clear();
								}
							}
							if (byteStack.size()>mPacketSize) { //if the stack has reached the packet size remove an element from the stack
								System.out.println("Throw Bytes "+ byteStack.get(0));
								byteStack.removeElementAt(0);
								
							}
						}
					}




				} else {
					
				}
			}
		}
	}
	
	private byte[] convertstacktobytearray(Stack<Byte> b,int packetSize) {
		byte[] returnByte=new byte[packetSize];
		b.remove(0); //remove the Data Packet identifier 
		for (int i=0;i<packetSize;i++) {
			returnByte[packetSize-1-i]=(byte) b.pop();
		}
		return returnByte;
	}
	
	protected void startResponseTimer(int duration) {
		// TODO Auto-generated method stub
		responseTimer(duration);
	}

	public synchronized void responseTimer(int seconds) {
		if (mTimer!=null) {
			mTimer.cancel();
			mTimer.purge();
		}
		printLogDataForDebugging("Waiting for ack/response for command: " + Integer.toString(mCurrentCommand));
		mTimer = new Timer();
		mTimer.schedule(new responseTask(), seconds*1000);
	}

	class responseTask extends TimerTask {
		public void run() {
			{
				if (mCurrentCommand==GET_FW_VERSION_COMMAND){
					printLogDataForDebugging("FW Response Timeout");
					//					mFWVersion=0.1;
					mFWMajorVersion=0;
					mFWMinorVersion=1;
					mFWInternal=0;
					mFWCode=0;
					mFWVersionFullName="BoilerPlate 0.1.0";
					mShimmerVersion = SHIMMER_2R; // on Shimmer2r has
					/*Message msg = mHandler.obtainMessage(MESSAGE_TOAST);
          	        Bundle bundle = new Bundle();
          	        bundle.putString(TOAST, "Firmware Version: " +mFWVersionFullName);
          	        msg.setData(bundle);*/
					if (!mDummy){
						//mHandler.sendMessage(msg);
					}
					mWaitForAck=false;
					mTransactionCompleted=true; //should be false, so the driver will know that the command has to be executed again, this is not supported at the moment 
					mTimer.cancel(); //Terminate the timer thread
					mTimer.purge();
					mFirstTime=false;
					mListofInstructions.remove(0);
					mInstructionStackLock=false;
					initializeBoilerPlate();
				} else if(mCurrentCommand==GET_SAMPLING_RATE_COMMAND && mInitialized==false){
					printLogDataForDebugging("Get Sampling Rate Timeout");
					mWaitForAck=false;
					mTransactionCompleted=true; //should be false, so the driver will know that the command has to be executed again, this is not supported at the moment 
					mTimer.cancel(); //Terminate the timer thread
					mTimer.purge();
					mFirstTime=false;
					mListofInstructions.remove(0);
					mInstructionStackLock=false;
					mFirstTime=false;
				} else if(mCurrentCommand==GET_SHIMMER_VERSION_COMMAND_NEW){ //in case the new command doesn't work, try the old command
					printLogDataForDebugging("Shimmer Version Response Timeout. Trying the old version command");
					mWaitForAck=false;
					mTransactionCompleted=true; 
					mTimer.cancel(); //Terminate the timer thread
					mTimer.purge();
					mFirstTime=false;
					mListofInstructions.remove(0);
					mInstructionStackLock=false;
					readShimmerVersionDepracated();
				}
				else if(mCurrentCommand==GET_STATUS_COMMAND){
					// If the command fails to get a response, the API should assume that the connection has been lost and close the serial port cleanly.

					System.out.println("Command " + Integer.toString(mCurrentCommand) +" failed");
					mWaitForAck=false;
					mTransactionCompleted=true;
					mTimer.cancel(); //Terminate the timer thread
					mTimer.purge();
					mListofInstructions.remove(0);
					mInstructionStackLock=false;
					mWaitForAck=false;
					mTransactionCompleted=true; //should be false, so the driver will know that the command has to be executed again, this is not supported at the moment 
					if (mStreaming && getPacketReceptionRate()<100){
						mListofInstructions.clear();
						printLogDataForDebugging("Response not received for Get_Status_Command. Loss bytes detected.");
					} else {
						//CODE TO BE USED
						printLogDataForDebugging("Command " + Integer.toString(mCurrentCommand) +" failed; Killing Connection. Packet RR:  " + Double.toString(getPacketReceptionRate()));
						if (mWaitForResponse){
							printLogDataForDebugging("Response not received");
							sendStatusMSGtoUI("Connection lost." + mMyBluetoothAddress);
						}
						stop(); //If command fail exit device
					}
				}
				else if(mCurrentCommand==GET_DIR_COMMAND){
					// If the command fails to get a response, the API should assume that the connection has been lost and close the serial port cleanly.

					System.out.println("Command " + Integer.toString(mCurrentCommand) +" failed");
					mWaitForAck=false;
					mTransactionCompleted=true;
					mTimer.cancel(); //Terminate the timer thread
					mTimer.purge();
					mListofInstructions.remove(0);
					mInstructionStackLock=false;
					mWaitForAck=false;
					mTransactionCompleted=true; //should be false, so the driver will know that the command has to be executed again, this is not supported at the moment 
					if (mStreaming && getPacketReceptionRate()<100){
						printLogDataForDebugging("Response not received for Get_Dir_Command. Loss bytes detected.");
						mListofInstructions.clear();
					} else {
						//CODE TO BE USED
						printLogDataForDebugging("Command " + Integer.toString(mCurrentCommand) +" failed; Killing Connection  ");
						if (mWaitForResponse){
							printLogDataForDebugging("Response not received");
							sendStatusMSGtoUI("Connection lost." + mMyBluetoothAddress);
						}
						stop(); //If command fail exit device
					}
				}
				else {
					printLogDataForDebugging("Command " + Integer.toString(mCurrentCommand) +" failed; Killing Connection  ");
					if (mWaitForResponse){
						printLogDataForDebugging("Response not received");
						sendStatusMSGtoUI("Response not received, please reset Shimmer Device." + mMyBluetoothAddress);
					}
					mWaitForAck=false;
					mTransactionCompleted=true; //should be false, so the driver will know that the command has to be executed again, this is not supported at the moment 
					stop(); //If command fail exit device 

				}
			}
		}
	}
	
	//endregion
	
	
	protected void reset(){
		mFWCode=0;
		//		protected double mFWVersion;
		mFWMajorVersion = 0;
		mFWMinorVersion = 0;
		mFWInternal = 0;
		mFWIdentifier = 0;
		mFWVersionFullName="";
		
	}

	//region --------- INITIALIZE SHIMMER FUNCTIONS --------- 
	
	protected synchronized void initialize() {	    	//See two constructors for Shimmer
		//InstructionsThread instructionsThread = new InstructionsThread();
		//instructionsThread.start();
		reset();
		if (mTimerToReadStatus!=null) {
			mTimerToReadStatus.cancel();
			mTimerToReadStatus.purge();
		}
		
		if (mTimer!=null){
			mTimer.cancel();
			mTimer.purge();
		}
		mInstructionStackLock = false;
		dummyreadSamplingRate(); // it actually acts to clear the write buffer
		readShimmerVersion();
		//readFWVersion();
		//mShimmerVersion=4;

	}

	public void initializeBoilerPlate(){
		readSamplingRate();
		readConfigByte0();
		readCalibrationParameters("Accelerometer");
		readCalibrationParameters("Magnetometer");
		readCalibrationParameters("Gyroscope");
		if (mSetupDevice==true && mShimmerVersion!=4){
			writeAccelRange(mAccelRange);
			writeGSRRange(mGSRRange);
			writeSamplingRate(mSamplingRate);	
			writeEnabledSensors(mSetEnabledSensors);
			setContinuousSync(mContinousSync);
		} else {
			inquiry();
		}
	}
	
	/**
	 * By default once connected no low power modes will be enabled. Low power modes should be enabled post connection once the MSG_STATE_FULLY_INITIALIZED is sent 
	 */
	private void initializeShimmer2R(){ 
		readSamplingRate();
		readMagSamplingRate();
		writeBufferSize(1);
		readBlinkLED();
		readConfigByte0();
		readCalibrationParameters("All");
		if (mSetupDevice==true){
			writeMagRange(mMagGain); //set to default Shimmer mag gain
			writeAccelRange(mAccelRange);
			writeGSRRange(mGSRRange);
			writeSamplingRate(mSamplingRate);	
			writeEnabledSensors(mSetEnabledSensors);
			setContinuousSync(mContinousSync);
		} else {
			if (mFWVersionFullName.equals("BoilerPlate 0.1.0")){
			
			} else {
				readMagRange();
			}
			inquiry();
		}
	}

	private void initializeShimmer3(){
		readSamplingRate();
		readMagRange();
		readAccelRange();
		readGyroRange();
		readAccelSamplingRate();
		readBlinkLED();
		readCalibrationParameters("All");
		readpressurecalibrationcoefficients();
		readEXGConfigurations(1);
		readEXGConfigurations(2);
		//enableLowPowerMag(mLowPowerMag);
		if (mSetupDevice==true){
			//writeAccelRange(mAccelRange);
			if (mSetupEXG){
				writeEXGConfiguration(mEXG1Register,1);
				writeEXGConfiguration(mEXG2Register,2);
				mSetupEXG = false;
			}
			writeGSRRange(mGSRRange);
			writeAccelRange(mAccelRange);
			writeGyroRange(mGyroRange);
			writeMagRange(mMagGain);
			writeSamplingRate(mSamplingRate);	
			writeInternalExpPower(1);
//			setContinuousSync(mContinousSync);
			writeEnabledSensors(mSetEnabledSensors); //this should always be the last command
		} else {
			inquiry();
		}
		
		
		if(mFWIdentifier==3){ // if shimmer is using LogAndStream FW, read its status perdiocally
			if (mTimerToReadStatus!=null) {
				mTimerToReadStatus.cancel();
				mTimerToReadStatus.purge();
			}
			printLogDataForDebugging("Waiting for ack/response for command: " + Integer.toString(mCurrentCommand));
			mTimerToReadStatus = new Timer();
			mTimerToReadStatus.schedule(new readStatusTask(), mReadStatusPeriod, mReadStatusPeriod);
		}
	}
	
	//endregion

	
	//region  --------- START/STOP STREAMING FUNCTIONS --------- 
	
	public void startStreaming() {
		//mCurrentLEDStatus=-1;	
		//provides a callback for users to initialize their algorithms when start streaming is called
		if (mDataProcessing!=null){
			mDataProcessing.InitializeProcessData();
		} 	
		else {
			//do nothing
		}
		
		if(mFWIdentifier==3){ // if shimmer is using LogAndStream FW, stop reading its status perdiocally
			if(mTimerToReadStatus!=null){
				mTimerToReadStatus.cancel();
				mTimerToReadStatus.purge();
				mTimerToReadStatus = null;
			}
		}
		
		mPacketLossCount = 0;
		mPacketReceptionRate = 100;
		mFirstTimeCalTime=true;
		mLastReceivedCalibratedTimeStamp = -1;
		mSync=true; // a backup sync done every time you start streaming
		mListofInstructions.add(new byte[]{START_STREAMING_COMMAND});
	}
	
	public void startDataLogAndStreaming(){
		if(mFWIdentifier==3){ // if shimmer is using LogAndStream FW, stop reading its status perdiocally

			if (mDataProcessing!=null){
				mDataProcessing.InitializeProcessData();
			} 	
			else {
				//do nothing
			}


			if(mTimerToReadStatus!=null){
				mTimerToReadStatus.cancel();
				mTimerToReadStatus.purge();
				mTimerToReadStatus = null;
			}

			mPacketLossCount = 0;
			mPacketReceptionRate = 100;
			mFirstTimeCalTime=true;
			mLastReceivedCalibratedTimeStamp = -1;
			mSync=true; // a backup sync done every time you start streaming
			mListofInstructions.add(new byte[]{START_SDBT_COMMAND});
		}
	}
	
	
	public void stopStreaming() {
		mListofInstructions.add(new byte[]{STOP_STREAMING_COMMAND});
		if(mFWIdentifier==3){ // if shimmer is using LogAndStream FW, read its status perdiocally
			if(mTimerToReadStatus==null){ 
				mTimerToReadStatus = new Timer();
			}
			mTimerToReadStatus.schedule(new readStatusTask(), mReadStatusPeriod, mReadStatusPeriod);
			}
		}
	
	
	//endregion
	
	
	//region --------- READ FUNCTIONS --------- 
	
	public void readShimmerVersion() {
		mDummy=false;//false
//		if (mFWVersionFullName.equals("BoilerPlate 0.1.0")){
//			mShimmerVersion = SHIMMER_2R; // on Shimmer2r has 
			
//		} else if (mFWVersion!=1.2){
			mListofInstructions.add(new byte[]{GET_SHIMMER_VERSION_COMMAND_NEW});
//		} else {
//			mListofInstructions.add(new byte[]{GET_SHIMMER_VERSION_COMMAND});
//		}
	}
	
	@Deprecated
	public void readShimmerVersionDepracated(){
		mListofInstructions.add(new byte[]{GET_SHIMMER_VERSION_COMMAND});
	}
	
	/**
	 * The reason for this is because sometimes the 1st response is not received by the phone
	 */
	protected void dummyreadSamplingRate() {
		mDummy=true;
		mListofInstructions.add(new byte[]{GET_SAMPLING_RATE_COMMAND});
	}

	/**
	 * This reads the configuration of a chip from the EXG board
	 * @param chipID Chip id can either be 1 or 2
	 */
	public void readEXGConfigurations(int chipID){
		if ((mFWInternal >=8 && mFWCode==2) || mFWCode>2){
			if (chipID==1 || chipID==2){
				mListofInstructions.add(new byte[]{GET_EXG_REGS_COMMAND,(byte)(chipID-1),0,10});
			}
		}
	}

	public void readpressurecalibrationcoefficients() {
		if (mShimmerVersion == SHIMMER_3){
			if (mFWCode>1){
				mListofInstructions.add(new byte[]{ GET_BMP180_CALIBRATION_COEFFICIENTS_COMMAND});
			}
		}
	}

	
	/**
	 * @param sensor is a string value that defines the sensor. Accepted sensor values are "Accelerometer","Gyroscope","Magnetometer","ECG","EMG","All"
	 */
	public void readCalibrationParameters(String sensor) {
	
			if (!mInitialized){
				if (mFWCode==1 && mFWInternal==0  && mShimmerVersion!=3) {
					//mFWVersionFullName="BoilerPlate 0.1.0";
					/*Message msg = mHandler.obtainMessage(MESSAGE_TOAST);
          	        Bundle bundle = new Bundle();
          	        bundle.putString(TOAST, "Firmware Version: " +mFWVersionFullName);
          	        msg.setData(bundle);
          	        mHandler.sendMessage(msg);*/
				}	
			}
			if (sensor.equals("Accelerometer")) {
				mListofInstructions.add(new byte[]{GET_ACCEL_CALIBRATION_COMMAND});
			}
			else if (sensor.equals("Gyroscope")) {
				mListofInstructions.add(new byte[]{GET_GYRO_CALIBRATION_COMMAND});
			}
			else if (sensor.equals("Magnetometer")) {
				mListofInstructions.add(new byte[]{GET_MAG_CALIBRATION_COMMAND});
			}
			else if (sensor.equals("All")){
				mListofInstructions.add(new byte[]{GET_ALL_CALIBRATION_COMMAND});
			} 
			else if (sensor.equals("ECG")){
				mListofInstructions.add(new byte[]{GET_ECG_CALIBRATION_COMMAND});
			} 
			else if (sensor.equals("EMG")){
				mListofInstructions.add(new byte[]{GET_EMG_CALIBRATION_COMMAND});
			}
		
	}
	
	public void readSamplingRate() {
		mListofInstructions.add(new byte[]{GET_SAMPLING_RATE_COMMAND});
	}
	
	public void readGSRRange() {
		mListofInstructions.add(new byte[]{GET_GSR_RANGE_COMMAND});
	}

	public void readAccelRange() {
		mListofInstructions.add(new byte[]{GET_ACCEL_SENSITIVITY_COMMAND});
	}

	public void readGyroRange() {
		mListofInstructions.add(new byte[]{GET_MPU9150_GYRO_RANGE_COMMAND});
	}

	public void readBufferSize() {
		mListofInstructions.add(new byte[]{GET_BUFFER_SIZE_COMMAND});
	}

	public void readMagSamplingRate() {
		if (mFWVersionFullName.equals("BoilerPlate 0.1.0")){
		} else {
			mListofInstructions.add(new byte[]{GET_MAG_SAMPLING_RATE_COMMAND});
		}
	}

	/**
	 * Used to retrieve the data rate of the Accelerometer on Shimmer 3
	 */
	public void readAccelSamplingRate() {
		if (mShimmerVersion!=3){
		} else {
			mListofInstructions.add(new byte[]{GET_ACCEL_SAMPLING_RATE_COMMAND});
		}
	}

	public void readMagRange() {
		mListofInstructions.add(new byte[]{GET_MAG_GAIN_COMMAND});
	}

	public void readBlinkLED() {
		mListofInstructions.add(new byte[]{GET_BLINK_LED});
	}
	
	public void readECGCalibrationParameters() {
		if (mFWVersionFullName.equals("BoilerPlate 0.1.0")){
		} else {
			mListofInstructions.add(new byte[]{GET_ECG_CALIBRATION_COMMAND});
		}
	}

	public void readEMGCalibrationParameters() {
		if (mFWVersionFullName.equals("BoilerPlate 0.1.0")){
		} else {
			mListofInstructions.add(new byte[]{GET_EMG_CALIBRATION_COMMAND});
		}
	}
	
	public void readBaudRate(){
		if(mFWCode>=5){ 
			mListofInstructions.add(new byte[]{GET_BAUD_RATE_COMMAND});
		}
	}
	
	/**
	 * Read the number of bytes specified starting in the offset from the expansion board attached to the Shimmer Device
	 * @param numBytes number of bytes to be read. there can be read up to 256 bytes
	 * @param offset point from where the function starts to read
	 */
	public void readExpansionBoardByBytes(int numBytes, int offset){
		if(mFWCode>=5){ 
			if(numBytes+offset<=256){
				numBytesToReadFromExpBoard = numBytes;
				mListofInstructions.add(new byte[]{GET_DAUGHTER_CARD_ID_COMMAND, (byte) numBytes, (byte) offset});
			}
		}
	}

	public void readExpansionBoardID(){
		if(mFWCode>=5){ 
			numBytesToReadFromExpBoard=3;
			int offset=0;
			mListofInstructions.add(new byte[]{GET_DAUGHTER_CARD_ID_COMMAND, (byte) numBytesToReadFromExpBoard, (byte) offset});
		}
	}
	
	public void readDirectoryName(){
		if(mFWIdentifier==3){ // check if Shimmer is using LogAndStream firmware
			mListofInstructions.add(new byte[]{GET_DIR_COMMAND});
		}
	}
	
	public void readStatusLogAndStream(){
		if(mFWIdentifier==3){ // check if Shimmer is using LogAndStream firmware
			mListofInstructions.add(new byte[]{GET_STATUS_COMMAND});
			System.out.println("Instrucction added to the list");
		}
	}

	public void readConfigByte0() {
		mListofInstructions.add(new byte[]{GET_CONFIG_BYTE0_COMMAND});
	}
	
	public void readFWVersion() {
		mDummy=false;//false
		mListofInstructions.add(new byte[]{GET_FW_VERSION_COMMAND});
	}
	
	/**
	 * Class used to read perdiocally the shimmer status when LogAndStream FW is installed
	 */
	public class readStatusTask extends TimerTask{

		@Override
		public void run() {
			// TODO Auto-generated method stub
			mListofInstructions.add(new byte[]{GET_STATUS_COMMAND});
		}
		
	}
	
	//endregion
	
	
	//region --------- WRITE FUNCTIONS --------- 
	
	
	/**
	 * writeGyroSamplingRate(range) sets the GyroSamplingRate on the Shimmer (version 3) to the value of the input range.
	 * @param rate it is a value between 0 and 255; 6 = 1152Hz, 77 = 102.56Hz, 255 = 31.25Hz
	 */
	private void writeGyroSamplingRate(int rate) {
		if (mShimmerVersion == SHIMMER_3){
			mTempIntValue=rate;
			mListofInstructions.add(new byte[]{SET_MPU9150_SAMPLING_RATE_COMMAND, (byte)rate});
		}
	}
	
	/**
	 * writeMagSamplingRate(range) sets the MagSamplingRate on the Shimmer to the value of the input range.
	 * @param rate for Shimmer 2 it is a value between 1 and 6; 0 = 0.5 Hz; 1 = 1.0 Hz; 2 = 2.0 Hz; 3 = 5.0 Hz; 4 = 10.0 Hz; 5 = 20.0 Hz; 6 = 50.0 Hz, for Shimmer 3 it is a value between 0-7; 0 = 0.75Hz; 1 = 1.5Hz; 2 = 3Hz; 3 = 7.5Hz; 4 = 15Hz ; 5 = 30 Hz; 6 = 75Hz ; 7 = 220Hz 
	 * 
	 * */
	private void writeMagSamplingRate(int rate) {
		if (mFWVersionFullName.equals("BoilerPlate 0.1.0")){
		} else {
			mTempIntValue=rate;
			mListofInstructions.add(new byte[]{SET_MAG_SAMPLING_RATE_COMMAND, (byte)rate});
		}
	}
	
	/**
	 * writeAccelSamplingRate(range) sets the AccelSamplingRate on the Shimmer (version 3) to the value of the input range.
	 * @param rate it is a value between 1 and 7; 1 = 1 Hz; 2 = 10 Hz; 3 = 25 Hz; 4 = 50 Hz; 5 = 100 Hz; 6 = 200 Hz; 7 = 400 Hz
	 */
	private void writeAccelSamplingRate(int rate) {
		if (mShimmerVersion == SHIMMER_3){
			mTempIntValue=rate;
			mListofInstructions.add(new byte[]{SET_ACCEL_SAMPLING_RATE_COMMAND, (byte)rate});
		}
	}
	
	/**
	 * Transmits a command to the Shimmer device to enable the sensors. To enable multiple sensors an or operator should be used (e.g. writeEnabledSensors(SENSOR_ACCEL|SENSOR_GYRO|SENSOR_MAG)). Command should not be used consecutively. Valid values are SENSOR_ACCEL, SENSOR_GYRO, SENSOR_MAG, SENSOR_ECG, SENSOR_EMG, SENSOR_GSR, SENSOR_EXP_BOARD_A7, SENSOR_EXP_BOARD_A0, SENSOR_BRIDGE_AMP and SENSOR_HEART.
    SENSOR_BATT
	 * @param enabledSensors e.g SENSOR_ACCEL|SENSOR_GYRO|SENSOR_MAG
	 */
	public void writeEnabledSensors(int enabledSensors) {
		
		if (!sensorConflictCheck(enabledSensors)){ //sensor conflict check
		
		} else {
			enabledSensors=generateSensorBitmapForHardwareControl(enabledSensors);
			tempEnabledSensors=enabledSensors;

			byte secondByte=(byte)((enabledSensors & 65280)>>8);
			byte firstByte=(byte)(enabledSensors & 0xFF);

			//write(new byte[]{SET_SENSORS_COMMAND,(byte) lowByte, highByte});
			if (mShimmerVersion == SHIMMER_3){
				byte thirdByte=(byte)((enabledSensors & 16711680)>>16);

				mListofInstructions.add(new byte[]{SET_SENSORS_COMMAND,(byte) firstByte,(byte) secondByte,(byte) thirdByte});
			} else {
				mListofInstructions.add(new byte[]{SET_SENSORS_COMMAND,(byte) firstByte,(byte) secondByte});
			}
			inquiry();
			
		}
	}
		
	/**
	 * writePressureResolution(range) sets the resolution of the pressure sensor on the Shimmer3
	 * @param settinge Numeric value defining the desired resolution of the pressure sensor. Valid range settings are 0 (low), 1 (normal), 2 (high), 3 (ultra high)
	 * 
	 * */
	public void writePressureResolution(int setting) {
		if (mShimmerVersion==SHIMMER_3){
			mListofInstructions.add(new byte[]{SET_BMP180_PRES_RESOLUTION_COMMAND, (byte)setting});
		}
	}

	/**
	 * writeAccelRange(range) sets the Accelerometer range on the Shimmer to the value of the input range. When setting/changing the accel range, please ensure you have the correct calibration parameters. Note that the Shimmer device can only carry one set of accel calibration parameters at a single time.
	 * @param range is a numeric value defining the desired accelerometer range. Valid range setting values for the Shimmer 2 are 0 (+/- 1.5g), 1 (+/- 2g), 2 (+/- 4g) and 3 (+/- 6g). Valid range setting values for the Shimmer 2r are 0 (+/- 1.5g) and 3 (+/- 6g).
	 */
	public void writeAccelRange(int range) {
		mListofInstructions.add(new byte[]{SET_ACCEL_SENSITIVITY_COMMAND, (byte)range});
		mAccelRange=(int)range;
		
	}

	/**
	 * writeGyroRange(range) sets the Gyroscope range on the Shimmer3 to the value of the input range. When setting/changing the range, please ensure you have the correct calibration parameters.
	 * @param range is a numeric value defining the desired gyroscope range. 
	 */
	public void writeGyroRange(int range) {
		if (mShimmerVersion==SHIMMER_3){
			mListofInstructions.add(new byte[]{SET_MPU9150_GYRO_RANGE_COMMAND, (byte)range});
			mGyroRange=(int)range;
		}
	}

	/**
	 * @param rate Defines the sampling rate to be set (e.g.51.2 sets the sampling rate to 51.2Hz). User should refer to the document Sampling Rate Table to see all possible values.
	 */
	public void writeSamplingRate(double rate) {
		if (mInitialized=true) {

			if (mShimmerVersion==SHIMMER_2 || mShimmerVersion==SHIMMER_2R){
				if (!mLowPowerMag){
					if (rate<=10) {
						writeMagSamplingRate(4);
					} else if (rate<=20) {
						writeMagSamplingRate(5);
					} else {
						writeMagSamplingRate(6);
					}
				} else {
					writeMagSamplingRate(4);
				}
				rate=1024/rate; //the equivalent hex setting
				mListofInstructions.add(new byte[]{SET_SAMPLING_RATE_COMMAND, (byte)Math.rint(rate), 0x00});
			} else if (mShimmerVersion==SHIMMER_3) {
				if (!mLowPowerMag){
					if (rate<=1) {
						writeMagSamplingRate(1);
					} else if (rate<=15) {
						writeMagSamplingRate(4);
					} else if (rate<=30){
						writeMagSamplingRate(5);
					} else if (rate<=75){
						writeMagSamplingRate(6);
					} else {
						writeMagSamplingRate(7);
					}
				} else {
					if (rate >=10){
						writeMagSamplingRate(4);
					} else {
						writeMagSamplingRate(1);
					}
				}

				if (!mLowPowerAccel){
					if (rate<=1){
						writeAccelSamplingRate(1);
					} else if (rate<=10){
						writeAccelSamplingRate(2);
					} else if (rate<=25){
						writeAccelSamplingRate(3);
					} else if (rate<=50){
						writeAccelSamplingRate(4);
					} else if (rate<=100){
						writeAccelSamplingRate(5);
					} else if (rate<=200){
						writeAccelSamplingRate(6);
					} else {
						writeAccelSamplingRate(7);
					}
				}
				else {
					if (rate>=10){
						writeAccelSamplingRate(2);
					} else {
						writeAccelSamplingRate(1);
					}
				}

				if (!mLowPowerGyro){
					if (rate<=51.28){
						writeGyroSamplingRate(0x9B);
					} else if (rate<=102.56){
						writeGyroSamplingRate(0x4D);
					} else if (rate<=129.03){
						writeGyroSamplingRate(0x3D);
					} else if (rate<=173.91){
						writeGyroSamplingRate(0x2D);
					} else if (rate<=205.13){
						writeGyroSamplingRate(0x26);
					} else if (rate<=258.06){
						writeGyroSamplingRate(0x1E);
					} else if (rate<=533.33){
						writeGyroSamplingRate(0xE);
					} else {
						writeGyroSamplingRate(6);
					}
				}
				else {
					writeGyroSamplingRate(0xFF);
				}

				

				int samplingByteValue = (int) (32768/rate);
				mListofInstructions.add(new byte[]{SET_SAMPLING_RATE_COMMAND, (byte)(samplingByteValue&0xFF), (byte)((samplingByteValue>>8)&0xFF)});




			}
		}
	}
	
	/**
	 * Only supported on Shimmer3, note that unlike previous write commands where the values are only set within the instrument driver after the ACK is received, this is set immediately. Fail safe should the settings not be actually set successfully is a timeout will occur, and the ID will disconnect from the device
	 * This function set the treshold of the ExG Lead-Off Comparator. There are 8 possible values:
	 * 1. Pos:95% - Neg:5%, 2. Pos:92.5% - Neg:7.5%, 3. Pos:90% - Neg:10%, 4. Pos:87.5% - Neg:12.5%, 5. Pos:85% - Neg:15%,
	 * 6. Pos:80% - Neg:20%, 7. Pos:75% - Neg:25%, 8. Pos:70% - Neg:30%
	 * @param treshold where 0 = 95-5, 1 = 92.5-7.5, 2 = 90-10, 3 = 87.5-12.5, 4 = 85-15, 5 = 80-20, 6 = 75-25, 7 = 70-30
	 */
	public void writeEXGLeadOffComparatorTreshold(int treshold){
		if(mFWCode>2){
			if(treshold >=0 && treshold<8){ 
				byte[] reg1 = mEXG1Register;
				byte[] reg2 = mEXG2Register;
				byte currentLeadOffTresholdChip1 = reg1[2];
				byte currentLeadOffTresholdChip2 = reg2[2];
				currentLeadOffTresholdChip1 = (byte) (currentLeadOffTresholdChip1 & 31);
				currentLeadOffTresholdChip2 = (byte) (currentLeadOffTresholdChip2 & 31);
				currentLeadOffTresholdChip1 = (byte) (currentLeadOffTresholdChip1 | (treshold<<5));
				currentLeadOffTresholdChip2 = (byte) (currentLeadOffTresholdChip2 | (treshold<<5));
				mListofInstructions.add(new byte[]{SET_EXG_REGS_COMMAND,(byte) 0,0,10,reg1[0],reg1[1],currentLeadOffTresholdChip1,reg1[3],reg1[4],reg1[5],reg1[6],reg1[7],reg1[8],reg1[9]});
				mListofInstructions.add(new byte[]{SET_EXG_REGS_COMMAND,(byte) 1,0,10,reg2[0],reg2[1],currentLeadOffTresholdChip2,reg2[3],reg2[4],reg2[5],reg2[6],reg2[7],reg2[8],reg2[9]});
			}
		}
	}
	
	
	/**
	 * Only supported on Shimmer3, note that unlike previous write commands where the values are only set within the instrument driver after the ACK is received, this is set immediately. Fail safe should the settings not be actually set successfully is a timeout will occur, and the ID will disconnect from the device
	 * This function set the ExG Lead-Off Current. There are 4 possible values: 6nA (default), 22nA, 6uA and 22uA.
	 * @param LeadOffCurrent where 0 = 6nA, 1 = 22nA, 2 = 6uA and 3 = 22uA
	 */
	public void writeEXGLeadOffDetectionCurrent(int leadOffCurrent){
		if(mFWCode>2){
			if(leadOffCurrent >=0 && leadOffCurrent<4){
				byte[] reg1 = mEXG1Register;
				byte[] reg2 = mEXG2Register;
				byte currentLeadOffDetectionCurrentChip1 = reg1[2];
				byte currentLeadOffDetectionCurrentChip2 = reg2[2];
				currentLeadOffDetectionCurrentChip1 = (byte) (currentLeadOffDetectionCurrentChip1 & 243);
				currentLeadOffDetectionCurrentChip2 = (byte) (currentLeadOffDetectionCurrentChip2 & 243);
				currentLeadOffDetectionCurrentChip1 = (byte) (currentLeadOffDetectionCurrentChip1 | (leadOffCurrent<<2));
				currentLeadOffDetectionCurrentChip2 = (byte) (currentLeadOffDetectionCurrentChip2 | (leadOffCurrent<<2));
				mListofInstructions.add(new byte[]{SET_EXG_REGS_COMMAND,(byte) 0,0,10,reg1[0],reg1[1],currentLeadOffDetectionCurrentChip1,reg1[3],reg1[4],reg1[5],reg1[6],reg1[7],reg1[8],reg1[9]});
				mListofInstructions.add(new byte[]{SET_EXG_REGS_COMMAND,(byte) 1,0,10,reg2[0],reg2[1],currentLeadOffDetectionCurrentChip2,reg2[3],reg2[4],reg2[5],reg2[6],reg2[7],reg2[8],reg2[9]});
			}
		}
	}
	
	
	/**
	 * Only supported on Shimmer3
	 * This function set the ExG Lead-Off detection mode. There are 3 possible modes: DC Current, AC Current (not supported yet), and Off.
	 * @param detectionMode where 0 = Off, 1 = DC Current, and 2 = AC Current
	 */
	public void writeEXGLeadOffDetectionMode(int detectionMode){
		
		if(mFWCode>2){
			if(detectionMode == 0){
				mLeadOffDetectionMode = detectionMode;
				byte[] reg1 = mEXG1Register;
				byte[] reg2 = mEXG2Register;
				byte currentComparatorChip1 = reg1[1];
				byte currentComparatorChip2 = reg2[1];
				currentComparatorChip1 = (byte) (currentComparatorChip1 & 191);
				currentComparatorChip2 = (byte) (currentComparatorChip2 & 191);
				byte currentRDLSense = reg1[5];
				currentRDLSense = (byte) (currentRDLSense & 239);
				byte current2P1N1P = reg1[6];
				current2P1N1P = (byte) (current2P1N1P & 240);
				byte current2P = reg2[6];
				current2P = (byte) (current2P & 240);
				mListofInstructions.add(new byte[]{SET_EXG_REGS_COMMAND,(byte) 0,0,10,reg1[0],currentComparatorChip1,reg1[2],reg1[3],reg1[4],currentRDLSense,current2P1N1P,reg1[7],reg1[8],reg1[9]});
				if(isEXGUsingDefaultEMGConfiguration()){
					byte currentEMGConfiguration = reg2[4];
					currentEMGConfiguration = (byte) (currentEMGConfiguration & 127);
					currentEMGConfiguration = (byte) (currentEMGConfiguration | 128);
					mListofInstructions.add(new byte[]{SET_EXG_REGS_COMMAND,(byte) 1,0,10,reg2[0],currentComparatorChip2,reg2[2],reg2[3],currentEMGConfiguration,reg2[5],current2P,reg2[7],reg2[8],reg2[9]});
				}
				else
					mListofInstructions.add(new byte[]{SET_EXG_REGS_COMMAND,(byte) 1,0,10,reg2[0],currentComparatorChip2,reg2[2],reg2[3],reg2[4],reg2[5],current2P,reg2[7],reg2[8],reg2[9]});
			}
			else if(detectionMode == 1){
				mLeadOffDetectionMode = detectionMode;
				
				byte[] reg1 = mEXG1Register;
				byte[] reg2 = mEXG2Register;
				byte currentDetectionModeChip1 = reg1[2];
				byte currentDetectionModeChip2 = reg2[2];
				currentDetectionModeChip1 = (byte) (currentDetectionModeChip1 & 254);	// set detection mode chip1 
				currentDetectionModeChip2 = (byte) (currentDetectionModeChip2 & 254);  // set detection mode chip2
				byte currentComparatorChip1 = reg1[1];
				byte currentComparatorChip2 = reg2[1];
				currentComparatorChip1 = (byte) (currentComparatorChip1 & 191);	
				currentComparatorChip2 = (byte) (currentComparatorChip2 & 191);
				currentComparatorChip1 = (byte) (currentComparatorChip1 | 64); // set comparator chip1 
				currentComparatorChip2 = (byte) (currentComparatorChip2 | 64); // set comparator chip2 
				byte currentRDLSense = reg1[5];
				currentRDLSense = (byte) (currentRDLSense & 239); 
				currentRDLSense = (byte) (currentRDLSense | 16); // set RLD sense
				byte current2P1N1P = reg1[6];
				current2P1N1P = (byte) (current2P1N1P & 240);
				current2P1N1P = (byte) (current2P1N1P | 7); // set 2P, 1N, 1P
				byte current2P = reg2[6];
				current2P = (byte) (current2P & 240);
				current2P = (byte) (current2P | 4); // set 2P
				
				mListofInstructions.add(new byte[]{SET_EXG_REGS_COMMAND,(byte) 0,0,10,reg1[0], currentComparatorChip1, currentDetectionModeChip1,reg1[3],reg1[4], currentRDLSense, current2P1N1P,reg1[7],reg1[8],reg1[9]});
				if(isEXGUsingDefaultEMGConfiguration()){ //if the EMG configuration is used, then enable the chanel 2 since it is needed for the Lead-off detection
					byte currentEMGConfiguration = reg2[4];
					currentEMGConfiguration = (byte) (currentEMGConfiguration & 127);
					mListofInstructions.add(new byte[]{SET_EXG_REGS_COMMAND,(byte) 1,0,10,reg2[0], currentComparatorChip2, currentDetectionModeChip2,reg2[3],currentEMGConfiguration,reg2[5],current2P,reg2[7],reg2[8],reg2[9]});
				}
				else
					mListofInstructions.add(new byte[]{SET_EXG_REGS_COMMAND,(byte) 1,0,10,reg2[0], currentComparatorChip2, currentDetectionModeChip2,reg2[3],reg2[4],reg2[5],current2P,reg2[7],reg2[8],reg2[9]});
			}
			else if(detectionMode == 2){
				mLeadOffDetectionMode = detectionMode;
				//NOT SUPPORTED YET
			}
		}
	}
	
	/**
	 * Only supported on Shimmer3, note that unlike previous write commands where the values are only set within the instrument driver after the ACK is received, this is set immediately. Fail safe should the settings not be actually set successfully is a timeout will occur, and the ID will disconnect from the device
	 * This function set the ExG reference electrode. There are 2 possible values when using ECG configuration: Inverse Wilson CT (default) and Fixed Potential
	 * and 2 possible values when using EMG configuration: Fixed Potential (default) and Inverse of Ch 1
	 * @param referenceElectrode reference electrode code where 0 = Fixed Potential and 13 = Inverse Wilson CT (default) for an ECG configuration, and
	 * 													where 0 = Fixed Potential (default) and 3 = Inverse Ch1 for an EMG configuration
	 */
	public void writeEXGReferenceElectrode(int referenceElectrode){
		if (mFWCode>2){
			byte currentByteValue = mEXG1Register[5];
			byte[] reg = mEXG1Register;
			currentByteValue = (byte) (currentByteValue & 240);
			currentByteValue = (byte) (currentByteValue | referenceElectrode);
			mListofInstructions.add(new byte[]{SET_EXG_REGS_COMMAND,(byte) 0,0,10,reg[0],reg[1],reg[2],reg[3],reg[4],currentByteValue,reg[6],reg[7],reg[8],reg[9]});
		}
	}

	/**
	 * Only supported on Shimmer3, note that unlike previous write commands where the values are only set within the instrument driver after the ACK is received, this is set immediately. Fail safe should the settings not be actually set successfully is a timeout will occur, and the ID will disconnect from the device
	 * @param chipID Either a 1 or 2 value
	 * @param rateSettingsam , where 0=125SPS ; 1=250SPS; 2=500SPS; 3=1000SPS; 4=2000SPS  
	 */
	public void writeEXGRateSetting(int chipID, int rateSetting){
		if ((mFWInternal >=8 && mFWCode==2) || mFWCode>2){
			if (chipID==1 || chipID==2){
				if (chipID==1){
					byte currentByteValue = mEXG1Register[0];
					byte[] reg = mEXG1Register;
					currentByteValue = (byte) (currentByteValue & 248);
					currentByteValue = (byte) (currentByteValue | rateSetting);
					mListofInstructions.add(new byte[]{SET_EXG_REGS_COMMAND,(byte) (chipID-1),0,10,currentByteValue,reg[1],reg[2],reg[3],reg[4],reg[5],reg[6],reg[7],reg[8],reg[9]});
				} else if (chipID==2){
					byte currentByteValue = mEXG2Register[0];
					byte[] reg = mEXG2Register;
					currentByteValue = (byte) (currentByteValue & 248);
					currentByteValue = (byte) (currentByteValue | rateSetting);
					mListofInstructions.add(new byte[]{SET_EXG_REGS_COMMAND,(byte) (chipID-1),0,10,currentByteValue,reg[1],reg[2],reg[3],reg[4],reg[5],reg[6],reg[7],reg[8],reg[9]});
				}
			}
		}
	}
	
	
	/**
	 * This is only supported on SHimmer3,, note that unlike previous write commands where the values are only set within the instrument driver after the ACK is received, this is set immediately. Fail safe should the settings not be actually set successfully is a timeout will occur, and the ID will disconnect from the device 
	 * @param chipID Either a 1 or 2 value
	 * @param gainSetting , where 0 = 6x Gain, 1 = 1x , 2 = 2x , 3 = 3x, 4 = 4x, 5 = 8x, 6 = 12x
	 * @param channel Either a 1 or 2 value
	 */
	public void writeEXGGainSetting(int chipID,  int channel, int gainSetting){
		if ((mFWInternal >=8 && mFWCode==2) || mFWCode>2){
			if ((chipID==1 || chipID==2) && (channel==1 || channel==2)){
				if (chipID==1){
					if (channel==1){
						byte currentByteValue = mEXG1Register[3];
						byte[] reg = mEXG1Register;
						currentByteValue = (byte) (currentByteValue & 143);
						currentByteValue = (byte) (currentByteValue | (gainSetting<<4));
						mListofInstructions.add(new byte[]{SET_EXG_REGS_COMMAND,(byte) (chipID-1),0,10,reg[0],reg[1],reg[2],currentByteValue,reg[4],reg[5],reg[6],reg[7],reg[8],reg[9]});
					} else {
						byte currentByteValue = mEXG1Register[4];
						byte[] reg = mEXG1Register;
						currentByteValue = (byte) (currentByteValue & 143);
						currentByteValue = (byte) (currentByteValue | (gainSetting<<4));
						mListofInstructions.add(new byte[]{SET_EXG_REGS_COMMAND,(byte) (chipID-1),0,10,reg[0],reg[1],reg[2],reg[3],currentByteValue,reg[5],reg[6],reg[7],reg[8],reg[9]});
					}
				} else if (chipID==2){
					if (channel==1){
						byte currentByteValue = mEXG2Register[3];
						byte[] reg = mEXG2Register;
						currentByteValue = (byte) (currentByteValue & 143);
						currentByteValue = (byte) (currentByteValue | (gainSetting<<4));
						mListofInstructions.add(new byte[]{SET_EXG_REGS_COMMAND,(byte) (chipID-1),0,10,reg[0],reg[1],reg[2],currentByteValue,reg[4],reg[5],reg[6],reg[7],reg[8],reg[9]});
					} else {
						byte currentByteValue = mEXG2Register[4];
						byte[] reg = mEXG2Register;
						currentByteValue = (byte) (currentByteValue & 143);
						currentByteValue = (byte) (currentByteValue | (gainSetting<<4));
						mListofInstructions.add(new byte[]{SET_EXG_REGS_COMMAND,(byte) (chipID-1),0,10,reg[0],reg[1],reg[2],reg[3],currentByteValue,reg[5],reg[6],reg[7],reg[8],reg[9]});
					}
				}
			}
		}
	}
	
	/**
	 * Only supported on Shimmer3, note that unlike previous write commands where the values are only set within the instrument driver after the ACK is received, this is set immediately. Fail safe should the settings not be actually set successfully is a timeout will occur, and the ID will disconnect from the device
	 * @param reg A 10 byte value
	 * @param chipID value can either be 1 or 2.
	 */
	public void writeEXGConfiguration(byte[] reg,int chipID){
		if ((mFWInternal >=8 && mFWCode==2) || mFWCode>2){
			if (chipID==1 || chipID==2){
				mListofInstructions.add(new byte[]{SET_EXG_REGS_COMMAND,(byte)(chipID-1),0,10,reg[0],reg[1],reg[2],reg[3],reg[4],reg[5],reg[6],reg[7],reg[8],reg[9]});
			}
		}}
		
	/**
	 * writeGSRRange(range) sets the GSR range on the Shimmer to the value of the input range. 
	 * @param range numeric value defining the desired GSR range. Valid range settings are 0 (10kOhm to 56kOhm), 1 (56kOhm to 220kOhm), 2 (220kOhm to 680kOhm), 3 (680kOhm to 4.7MOhm) and 4 (Auto Range).
	 */
	public void writeGSRRange(int range) {
		if (mShimmerVersion == SHIMMER_3){
			if (mFWCode!=1 || mFWInternal >4){
				mListofInstructions.add(new byte[]{SET_GSR_RANGE_COMMAND, (byte)range});
			}
		} else {
			mListofInstructions.add(new byte[]{SET_GSR_RANGE_COMMAND, (byte)range});
		}
	}
	
	/**
	 * writeMagRange(range) sets the MagSamplingRate on the Shimmer to the value of the input range. When setting/changing the accel range, please ensure you have the correct calibration parameters. Note that the Shimmer device can only carry one set of accel calibration parameters at a single time.
	 * @param range is the mag rang
	 */
	public void writeMagRange(int range) {
		if (mFWVersionFullName.equals("BoilerPlate 0.1.0")){
		} else {
			mListofInstructions.add(new byte[]{SET_MAG_GAIN_COMMAND, (byte)range});
		}
	}

	public void writeLEDCommand(int command) {
//		if (mShimmerVersion!=SHIMMER_3){
			if (mFWVersionFullName.equals("BoilerPlate 0.1.0")){
			} else {
				mListofInstructions.add(new byte[]{SET_BLINK_LED, (byte)command});
			}
//		}
	}

	public void writeAccelCalibrationParameters(byte[] calibrationParameters) {
		cmdcalibrationParameters[0] = SET_ACCEL_CALIBRATION_COMMAND;
		System.arraycopy(calibrationParameters, 0, cmdcalibrationParameters, 1, 21);
		mListofInstructions.add(cmdcalibrationParameters);	
	}
	
	public void writeGyroCalibrationParameters(byte[] calibrationParameters) {
		cmdcalibrationParameters[0] = SET_GYRO_CALIBRATION_COMMAND;
		System.arraycopy(calibrationParameters, 0, cmdcalibrationParameters, 1, 21);
		mListofInstructions.add(cmdcalibrationParameters);	
	}
	
	public void writeMagCalibrationParameters(byte[] calibrationParameters) {
		cmdcalibrationParameters[0] = SET_MAG_CALIBRATION_COMMAND;
		System.arraycopy(calibrationParameters, 0, cmdcalibrationParameters, 1, 21);
		mListofInstructions.add(cmdcalibrationParameters);	
	}

	public void writeWRAccelCalibrationParameters(byte[] calibrationParameters) {
		if(mShimmerVersion==SHIMMER_3){
			cmdcalibrationParameters[0] = SET_LSM303DLHC_ACCEL_CALIBRATION_COMMAND;
			System.arraycopy(calibrationParameters, 0, cmdcalibrationParameters, 1, 21);
			mListofInstructions.add(cmdcalibrationParameters);	
		}
	}

	public void writeECGCalibrationParameters(int offsetrall, int gainrall,int offsetlall, int gainlall) {
		byte[] data = new byte[8];
		data[0] = (byte) ((offsetlall>>8)& 0xFF); //MSB offset
		data[1] = (byte) ((offsetlall)& 0xFF);
		data[2] = (byte) ((gainlall>>8)& 0xFF); //MSB gain
		data[3] = (byte) ((gainlall)& 0xFF);
		data[4] = (byte) ((offsetrall>>8)& 0xFF); //MSB offset
		data[5] = (byte) ((offsetrall)& 0xFF);
		data[6] = (byte) ((gainrall>>8)& 0xFF); //MSB gain
		data[7] = (byte) ((gainrall)& 0xFF);
		if (mFWVersionFullName.equals("BoilerPlate 0.1.0")){
		} else {
			mListofInstructions.add(new byte[]{SET_ECG_CALIBRATION_COMMAND,data[0],data[1],data[2],data[3],data[4],data[5],data[6],data[7]});
		}
	}

	public void writeEMGCalibrationParameters(int offset, int gain) {
		byte[] data = new byte[4];
		data[0] = (byte) ((offset>>8)& 0xFF); //MSB offset
		data[1] = (byte) ((offset)& 0xFF);
		data[2] = (byte) ((gain>>8)& 0xFF); //MSB gain
		data[3] = (byte) ((gain)& 0xFF);
		if (mFWVersionFullName.equals("BoilerPlate 0.1.0")){
		} else {
			mListofInstructions.add(new byte[]{SET_EMG_CALIBRATION_COMMAND,data[0],data[1],data[2],data[3]});
		}
	}
	
	/**
	 * writeBaudRate(value) sets the baud rate on the Shimmer. 
	 * @param value numeric value defining the desired Baud rate. Valid rate settings are 0 (115200 default),
	 *  1 (1200), 2 (2400), 3 (4800), 4 (9600) 5 (19200),
	 *  6 (38400), 7 (57600), 8 (230400), 9 (460800) and 10 (921600)
	 */
	public void writeBaudRate(int value) {
		if (mFWCode>=5){ 
			if(value>=0 && value<=10){
				mListofInstructions.add(new byte[]{SET_BAUD_RATE_COMMAND, (byte)value});
				try {
					Thread.sleep(200);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				this.reconnect();
			}
			
		}
	}

	/**
	 * writeConfigByte0(configByte0) sets the config byte0 value on the Shimmer to the value of the input configByte0. 
	 * @param configByte0 is an unsigned 8 bit value defining the desired config byte 0 value.
	 */
	public void writeConfigByte0(byte configByte0) {
		mListofInstructions.add(new byte[]{SET_CONFIG_BYTE0_COMMAND,(byte) configByte0});
	}
	
	/**
	 * writeAccelRange(range) sets the Accelerometer range on the Shimmer to the value of the input range. When setting/changing the accel range, please ensure you have the correct calibration parameters. Note that the Shimmer device can only carry one set of accel calibration parameters at a single time.
	 * @param range is a numeric value defining the desired accelerometer range. Valid range setting values for the Shimmer 2 are 0 (+/- 1.5g), 1 (+/- 2g), 2 (+/- 4g) and 3 (+/- 6g). Valid range setting values for the Shimmer 2r are 0 (+/- 1.5g) and 3 (+/- 6g).
	 */
	public void writeBufferSize(int size) {
		mListofInstructions.add(new byte[]{SET_BUFFER_SIZE_COMMAND, (byte)size});
	}
	
	/**
	 * Sets the Pmux bit value on the Shimmer to the value of the input SETBIT. The PMux bit is the 2nd MSB of config byte0.
	 * @param setBit value defining the desired setting of the PMux (1=ON, 0=OFF).
	 */
	public void writePMux(int setBit) {
		mListofInstructions.add(new byte[]{SET_PMUX_COMMAND,(byte) setBit});
	}

	/**
	 * Sets the configGyroTempVref bit value on the Shimmer to the value of the input SETBIT. The configGyroTempVref bit is the 2nd MSB of config byte0.
	 * @param setBit value defining the desired setting of the Gyro Vref (1=ON, 0=OFF).
	 */
	/*public void writeConfigGyroTempVref(int setBit) {
    	while(getInstructionStatus()==false) {};
			//Bit value defining the desired setting of the PMux (1=ON, 0=OFF).
			if (setBit==1) {
				mTempByteValue=(byte) (mConfigByte0|32); 
			} else if (setBit==0) {
				mTempByteValue=(byte)(mConfigByte0 & 223);
			}
			mCurrentCommand=SET_GYRO_TEMP_VREF_COMMAND;
			write(new byte[]{SET_GYRO_TEMP_VREF_COMMAND,(byte) setBit});
			mWaitForAck=true;
			mTransactionCompleted=false;
			responseTimer(ACK_TIMER_DURATION);
	}*/

	/**
	 * Enable/disable the Internal Exp Power on the Shimmer3
	 * @param setBit value defining the desired setting of the Volt regulator (1=ENABLED, 0=DISABLED).
	 */
	public void writeInternalExpPower(int setBit) {
		if (mShimmerVersion == SHIMMER_3 && mFWCode>=2){
			mListofInstructions.add(new byte[]{SET_INTERNAL_EXP_POWER_ENABLE_COMMAND,(byte) setBit});
		} else {
			
		}
	}
	
	/**
	 * Enable/disable the 5 Volt Regulator on the Shimmer ExpBoard board
	 * @param setBit value defining the desired setting of the Volt regulator (1=ENABLED, 0=DISABLED).
	 */
	public void writeFiveVoltReg(int setBit) {
		mListofInstructions.add(new byte[]{SET_5V_REGULATOR_COMMAND,(byte) setBit});
	}
	
	//endregion
	
	
	//region --------- GET/SET FUNCTIONS --------- 
	
	/**** GET FUNCTIONS *****/

	/**
	 * This returns the variable mTransactionCompleted which indicates whether the Shimmer device is in the midst of a command transaction. True when no transaction is taking place. This is deprecated since the update to a thread model for executing commands
	 * @return mTransactionCompleted
	 */
	public boolean getInstructionStatus(){	
		boolean instructionStatus=false;
		if (mTransactionCompleted == true) {
			instructionStatus=true;
		} else {
			instructionStatus=false;
		}
		return instructionStatus;
	}
	
	public int getLowPowerAccelEnabled(){
		// TODO Auto-generated method stub
		if ( mLowPowerAccel)
			return 1;
		else
			return 0;
	}

	public int getLowPowerGyroEnabled() {
		// TODO Auto-generated method stub
		if ( mLowPowerGyro)
			return 1;
		else
			return 0;
	}

	public int getLowPowerMagEnabled() {
		// TODO Auto-generated method stub
		if ( mLowPowerMag)
			return 1;
		else
			return 0;
	}
	
	public int getPacketSize(){
		return mPacketSize;
	}
	
	public boolean getInitialized(){
		return mInitialized;
	}

	public double getPacketReceptionRate(){
		return mPacketReceptionRate;
	}
	
	/**
	 * Get the 5V Reg. Only supported on Shimmer2/2R.
	 * @return 0 in case the 5V Reg is disableb, 1 in case the 5V Reg is enabled, and -1 in case the device doesn't support this feature
	 */
	public int get5VReg(){
		if(mShimmerVersion!=SHIMMER_3){
			if ((mConfigByte0 & (byte)128)!=0) {
				//then set ConfigByte0 at bit position 7
				return 1;
			} else {
				return 0;
			}
		}
		else
			return -1;
	}
	
	public String getDirectoryName(){
		if(mDirectoryName!=null)
			return mDirectoryName;
		else
			return "Directory not read yet";
	}

	public int getCurrentLEDStatus() {
		return mCurrentLEDStatus;
	}

	public int getFirmwareMajorVersion(){
		return mFWMajorVersion;
	}
	
	public int getFirmwareMinorVersion(){
		return mFWMinorVersion;
	}
	
	public int getFirmwareCode(){
		return mFWCode;
	}
	
	public String getFWVersionName(){
		return mFWVersionFullName;
	}
	
	/**
	 * Get the FW Identifier. It is equal to 3 when LogAndStream, and equal to 4 when BTStream. 
	 * @return The FW identifier
	 */
	public int getFWIdentifier(){
		return (int) mFWIdentifier;
	}
	
	public int getBaudRate(){
		return mBaudRate;
	}
	
	public int getReferenceElectrode(){
		return mRefenceElectrode;
	}
	
	public int getLeadOffDetectionMode(){
		return mLeadOffDetectionMode;
	}
	
	public int getLeadOffDetectionCurrent(){
		return mLeadOffDetectionCurrent;
	}
	
	public int getLeadOffComparatorTreshold(){
		return mLeadOffComparatorTreshold;
	}
	
	public byte[] getExG1Register(){

	       return mEXG1Register;

	    }

	   

	public byte[] getExG2Register(){

	       return mEXG2Register;

	    }
	
	public int getExGComparatorsChip1(){
		return mComparatorsChip1;
	}
	
	public int getExGComparatorsChip2(){
		return mComparatorsChip2;
	}
	
	public String getExpBoardID(){
		
		if(mExpBoardArray!=null){
//			if(mExpBoardName==null){
				int boardID = mExpBoardArray[1] & 0xFF;
				int boardRev = mExpBoardArray[2] & 0xFF;
				int specialRevision = mExpBoardArray[3] & 0xFF;
				String boardName;
				switch(boardID){
					case 8:
						boardName="Bridge Amplifier+";
					break;
					case 14:
						boardName="GSR+";
					break;
					case 36:
						boardName="PROTO3 Mini";
					break;
					case 37:
						boardName="ExG";
					break;
					case 38:
						boardName="PROTO3 Deluxe";
					break;
					default:
						boardName="Unknown";
					break;
					
				}
				if(!boardName.equals("Unknown")){
					boardName += " (SR" + boardID + "." + boardRev + "." + specialRevision +")";
				}
				
				mExpBoardName = boardName;
//			}
		}
		else
			return "Need to read ExpBoard ID first";
		
		return mExpBoardName;
	}
	
	public double getBattLimitWarning(){
		return mLowBattLimit;
	}

	public int getShimmerVersion(){
		return mShimmerVersion;
	}

	public String getShimmerName(){
		return mMyName;
	}
	
	/**
	 * Get the Gain value for the ExG1 Channel 1
	 * @return the value of the gain. The Gain can be 1, 2, 3, 4, 6 (default), 8 or 12. The function return -1 when it is not possible to get the value.
	 */
	public int getEXG1CH1GainValue(){
		
		int gain = -1;
		while(!mListofInstructions.isEmpty());
		int tmpGain = getExg1CH1GainValue();
		if(tmpGain==1 || tmpGain==2 || tmpGain==3 || tmpGain==4 || tmpGain==6 || tmpGain==8 || tmpGain==12){
			gain = tmpGain;
		}
		return gain;
	}
	
	/**
	 * Get the Gain value for the ExG1 Channel 1
	 * @return the value of the gain. The Gain can be 1, 2, 3, 4, 6 (default), 8 or 12. The function return -1 when it is not possible to get the value.
	 */
	public int getEXG1CH2GainValue(){
		
		int gain = -1;
		while(!mListofInstructions.isEmpty());
		int tmpGain = getExg1CH2GainValue();
		if(tmpGain==1 || tmpGain==2 || tmpGain==3 || tmpGain==4 || tmpGain==6 || tmpGain==8 || tmpGain==12){
			gain = tmpGain;
		}
		return gain;
	}
	
	/**
	 * Get the Gain value for the ExG1 Channel 1
	 * @return the value of the gain. The Gain can be 1, 2, 3, 4, 6 (default), 8 or 12. The function return -1 when it is not possible to get the value.
	 */
	public int getEXG2CH1GainValue(){
		
		int gain = -1;
		while(!mListofInstructions.isEmpty());
		int tmpGain = getExg2CH1GainValue();
		if(tmpGain==1 || tmpGain==2 || tmpGain==3 || tmpGain==4 || tmpGain==6 || tmpGain==8 || tmpGain==12){
			gain = tmpGain;
		}
		return gain;
	}

	/**
	 * Get the Gain value for the ExG1 Channel 1
	 * @return the value of the gain. The Gain can be 1, 2, 3, 4, 6 (default), 8 or 12. The function return -1 when it is not possible to get the value.
	 */
	public int getEXG2CH2GainValue(){
	
		int gain = -1;
		while(!mListofInstructions.isEmpty());
		int tmpGain = getExg2CH2GainValue();
		if(tmpGain==1 || tmpGain==2 || tmpGain==3 || tmpGain==4 || tmpGain==6 || tmpGain==8 || tmpGain==12){
			gain = tmpGain;
		}
		return gain;
	}
	
    public int getState(){
        return mState;
    }

    
    /**** SET FUNCTIONS *****/
    
    /**
	 * 
	 * Register a callback to be invoked after buildmsg has executed (A new packet has been successfully received -> raw bytes interpreted into Raw and Calibrated Sensor data)
	 * 
	 * @param d The callback that will be invoked
	 */
	public void setDataProcessing(DataProcessing d) {
		mDataProcessing=d;
	}
    
	/**
	 * Set the battery voltage limit, when the Shimmer device goes below the limit while streaming the LED on the Shimmer device will turn Yellow, in order to use battery voltage monitoring the Battery has to be enabled. See writeenabledsensors. Only to be used with Shimmer2. Calibration also has to be enabled, see enableCalibration.
	 * @param limit
	 */
	public void setBattLimitWarning(double limit){
		mLowBattLimit=limit;
	}
	
	public void setContinuousSync(boolean continousSync){
		mContinousSync=continousSync;
	}
	
	//endregion
	
    
    //region --------- IS+something FUNCTIONS --------- 
    
    public boolean isLowPowerMagEnabled(){
		return mLowPowerMag;
	}
    
    public boolean isGyroOnTheFlyCalEnabled(){
		return mEnableOntheFlyGyroOVCal;
	}

	public boolean is3DOrientatioEnabled(){
		return mOrientationEnabled;
	}
    
	public boolean isSensing(){
		return mSensingStatus;
	}
	
	public boolean isDocked(){
		return mDockedStatus;
	}
	
	public boolean isLowPowerAccelEnabled() {
		// TODO Auto-generated method stub
		return mLowPowerAccel;
	}

	public boolean isLowPowerGyroEnabled() {
		// TODO Auto-generated method stub
		return mLowPowerGyro;
	}
	
	public boolean isUsingDefaultLNAccelParam(){
		return mDefaultCalibrationParametersAccel;
	}
	
	public boolean isUsingDefaultAccelParam(){
		return mDefaultCalibrationParametersAccel;
	}
	
	public boolean isUsingDefaultWRAccelParam(){
		return mDefaultCalibrationParametersDigitalAccel; 
	}

	public boolean isUsingDefaultGyroParam(){
		return mDefaultCalibrationParametersGyro;
	}
	
	public boolean isUsingDefaultMagParam(){
		return mDefaultCalibrationParametersMag;
	}
	
	public boolean isUsingDefaultECGParam(){
		return mDefaultCalibrationParametersECG;
	}
	
	public boolean isUsingDefaultEMGParam(){
		return mDefaultCalibrationParametersEMG;
	}
	
	/**
	 * Checks if 16 bit ECG configuration is set on the Shimmer device. Do not use this command right after setting an EXG setting, as due to the execution model, the old settings might be returned, if this command is executed before an ack is received.
	 * @return true if 16 bit ECG is set
	 */
	public boolean isEXGUsingECG16Configuration(){
		boolean using = false;
		while(!mListofInstructions.isEmpty());
		
			if ((mEnabledSensors & SENSOR_EXG1_16BIT)>0 && (mEnabledSensors & SENSOR_EXG2_16BIT)>0){
				if(isEXGUsingDefaultECGConfiguration()){
					using = true;
				}
			}
		 
		return using;
	}
	
	/**
	 * Checks if 24 bit ECG configuration is set on the Shimmer device. Do not use this command right after setting an EXG setting, as due to the execution model, the old settings might be returned, if this command is executed before an ack is received.
	 * @return true if 24 bit ECG is set
	 */
	public boolean isEXGUsingECG24Configuration(){
		boolean using = false;
		while(!mListofInstructions.isEmpty());
		if ((mEnabledSensors & SENSOR_EXG1_24BIT)>0 && (mEnabledSensors & SENSOR_EXG2_24BIT)>0){
			if(isEXGUsingDefaultECGConfiguration()){
				using = true;
			}
		}
		return using;
	}
	
	/**
	 * Checks if 16 bit EMG configuration is set on the Shimmer device.  Do not use this command right after setting an EXG setting, as due to the execution model, the old settings might be returned, if this command is executed before an ack is received. 
	 * @return true if 16 bit EMG is set
	 */
	public boolean isEXGUsingEMG16Configuration(){
		boolean using = false;
		while(!mListofInstructions.isEmpty());
		if ((mEnabledSensors & SENSOR_EXG1_16BIT)>0 && (mEnabledSensors & SENSOR_EXG2_16BIT)>0){
			if(isEXGUsingDefaultEMGConfiguration()){
				using = true;
			}
		}
		return using;
	}
	
	/**
	 * Checks if 24 bit EMG configuration is set on the Shimmer device.  Do not use this command right after setting an EXG setting, as due to the execution model, the old settings might be returned, if this command is executed before an ack is received.
	 * @return true if 24 bit EMG is set
	 */
	public boolean isEXGUsingEMG24Configuration(){
		boolean using = false;
		while(!mListofInstructions.isEmpty());
		if ((mEnabledSensors & SENSOR_EXG1_24BIT)>0 && (mEnabledSensors & SENSOR_EXG2_24BIT)>0){
			if(isEXGUsingDefaultEMGConfiguration()){
				using = true;
			}
		}
		return using;
	}
	
	/**
	 * Checks if 16 bit test signal configuration is set on the Shimmer device. Do not use this command right after setting an EXG setting, as due to the execution model, the old settings might be returned, if this command is executed before an ack is received.
	 * @return true if 24 bit test signal is set
	 */
	public boolean isEXGUsingTestSignal16Configuration(){
		boolean using = false;
		while(!mListofInstructions.isEmpty());
		if ((mEnabledSensors & SENSOR_EXG1_16BIT)>0 && (mEnabledSensors & SENSOR_EXG2_16BIT)>0){
			if(isEXGUsingDefaultTestSignalConfiguration()){
				using = true;
			}
		}
		return using;
	}
	
	/**
	 * Checks if 24 bit test signal configuration is set on the Shimmer device.
	 * @return true if 24 bit test signal is set
	 */
	public boolean isEXGUsingTestSignal24Configuration(){
		boolean using = false;
		while(!mListofInstructions.isEmpty());
		if ((mEnabledSensors & SENSOR_EXG1_24BIT)>0 && (mEnabledSensors & SENSOR_EXG2_24BIT)>0){
			if(isEXGUsingDefaultTestSignalConfiguration()){
				using = true;
			}
		}
		return using;
	}
	
    //endregion
    

	//region --------- ENABLE/DISABLE FUNCTIONS --------- 

	/**** ENABLE FUNCTIONS *****/
	
	/**
	 * This enables the calculation of 3D orientation through the use of the gradient descent algorithm, note that the user will have to ensure that mEnableCalibration has been set to true (see enableCalibration), and that the accel, gyro and mag has been enabled
	 * @param enable
	 */
	public void enable3DOrientation(boolean enable){
		//enable the sensors if they have not been enabled 
		mOrientationEnabled = enable;
	}

	/**
	 * This enables the low power accel option. When not enabled the sampling rate of the accel is set to the closest value to the actual sampling rate that it can achieve. In low power mode it defaults to 10Hz. Also and additional low power mode is used for the LSM303DLHC. This command will only supports the following Accel range +4g, +8g , +16g 
	 * @param enable
	 */
	public void enableLowPowerAccel(boolean enable){
		mLowPowerAccel = enable;
		if (!mLowPowerAccel){
			enableLowResolutionMode(false);
			if (mSamplingRate<=1){
				writeAccelSamplingRate(1);
			} else if (mSamplingRate<=10){
				writeAccelSamplingRate(2);
			} else if (mSamplingRate<=25){
				writeAccelSamplingRate(3);
			} else if (mSamplingRate<=50){
				writeAccelSamplingRate(4);
			} else if (mSamplingRate<=100){
				writeAccelSamplingRate(5);
			} else if (mSamplingRate<=200){
				writeAccelSamplingRate(6);
			} else {
				writeAccelSamplingRate(7);
			}
		}
		else {
			enableLowResolutionMode(true);
			writeAccelSamplingRate(2);
		}
	}

	/**
	 * This enables the low power accel option. When not enabled the sampling rate of the accel is set to the closest value to the actual sampling rate that it can achieve. In low power mode it defaults to 10Hz. Also and additional low power mode is used for the LSM303DLHC. This command will only supports the following Accel range +4g, +8g , +16g 
	 * @param enable
	 */
	public void enableLowPowerGyro(boolean enable){
		mLowPowerGyro = enable;
		if (!mLowPowerGyro){
			if (mSamplingRate<=51.28) {
				writeGyroSamplingRate(0x9B);
			} else if (mSamplingRate<=102.56) {
				writeGyroSamplingRate(0x4D);
			} else if (mSamplingRate<=129.03) {
				writeGyroSamplingRate(0x3D);
			} else if (mSamplingRate<=173.91) {
				writeGyroSamplingRate(0x2D);
			} else if (mSamplingRate<=205.13) {
				writeGyroSamplingRate(0x26);
			} else if (mSamplingRate<=258.06) {
				writeGyroSamplingRate(0x1E);
			} else if (mSamplingRate<=533.33) {
				writeGyroSamplingRate(0xE);
			} else {
				writeGyroSamplingRate(6);
			}
		}
		else {
			writeGyroSamplingRate(0xFF);
		}
	}
	
	private void enableLowResolutionMode(boolean enable){
		while(getInstructionStatus()==false) {};
		if (mFWCode==1 && mFWInternal==0){

		} else if (mShimmerVersion == SHIMMER_3){
			if (enable){
				mListofInstructions.add(new byte[]{SET_LSM303DLHC_ACCEL_LPMODE_COMMAND, (byte)0x01});
				mListofInstructions.add(new byte[]{SET_LSM303DLHC_ACCEL_HRMODE_COMMAND, (byte)0x00});

			} else {
				mListofInstructions.add(new byte[]{SET_LSM303DLHC_ACCEL_HRMODE_COMMAND, (byte)0x01});
				mListofInstructions.add(new byte[]{SET_LSM303DLHC_ACCEL_LPMODE_COMMAND, (byte)0x00});

			}

		}
	}
	
	/**
	 * This enables the low power mag option. When not enabled the sampling rate of the mag is set to the closest value to the actual sampling rate that it can achieve. In low power mode it defaults to 10Hz
	 * @param enable
	 */
	public void enableLowPowerMag(boolean enable){
		mLowPowerMag = enable;
		if (mShimmerVersion!=SHIMMER_3){
			if (!mLowPowerMag){
				if (mSamplingRate>=50){
					writeMagSamplingRate(6);
				} else if (mSamplingRate>=20) {
					writeMagSamplingRate(5);
				} else if (mSamplingRate>=10) {
					writeMagSamplingRate(4);
				} else {
					writeMagSamplingRate(3);
				}
			} else {
				writeMagSamplingRate(4);
			}
		} else {
			if (!mLowPowerMag){
				if (mSamplingRate<=1){
					writeMagSamplingRate(1);
				} else if (mSamplingRate<=15) {
					writeMagSamplingRate(4);
				} else if (mSamplingRate<=30) {
					writeMagSamplingRate(5);
				} else if (mSamplingRate<=75) {
					writeMagSamplingRate(6);
				} else {
					writeMagSamplingRate(7);
				}
			} else {
				if (mSamplingRate>=10){
					writeMagSamplingRate(4);
				} else {
					writeMagSamplingRate(1);
				}
			}
		}
	}
	
	/**
	 *This can only be used for Shimmer3 devices (EXG) 
	 *When a enable configuration is load, the advanced exg configuration is removed, so it needs to be set again
	 * 
	 */
	 public void enableDefaultECGConfiguration() {
		 if (mShimmerVersion==3){
			byte[] reg1 = {2,(byte) 160,16,64,64,45,0,0,2,3};
			byte[] reg2 = {2,(byte) 160,16,64,71,0,0,0,2,1};
			if (mSamplingRate<=128){
				reg1[0]=0;
				reg2[0]=0;
			} else if (mSamplingRate<=256){
				reg1[0]=1;
				reg2[0]=1;
			}
			else if (mSamplingRate<=512){
				reg1[0]=2;
				reg2[0]=2;
			}
			writeEXGConfiguration(reg1,1);
			writeEXGConfiguration(reg2,2);
		 }
	}

	/**
	 * This can only be used for Shimmer3 devices (EXG)
	 * When a enable configuration is load, the advanced exg configuration is removed, so it needs to be set again
	 */
	public void enableDefaultEMGConfiguration(){
		if (mShimmerVersion==3){
			byte[] reg1 = {2,(byte) 160,16,105,96,32,0,0,2,3};
			byte[] reg2 = {2,(byte) 160,16,(byte)129,(byte)129,0,0,0,2,1};
			if (mSamplingRate<=128){
				reg1[0]=0;
				reg2[0]=0;
			} else if (mSamplingRate<=256){
				reg1[0]=1;
				reg2[0]=1;
			}
			else if (mSamplingRate<=512){
				reg1[0]=2;
				reg2[0]=2;
			}
			writeEXGConfiguration(reg1,1);
			writeEXGConfiguration(reg2,2);
		}
		
	}

	/**
	 * This can only be used for Shimmer3 devices (EXG). Enables the test signal (square wave) of both EXG chips, to use, both EXG1 and EXG2 have to be enabled
	 */
	public void enableEXGTestSignal(){
		if (mShimmerVersion==3){
			byte[] reg1 = {2,(byte) 163,16,5,5,0,0,0,2,1};
			byte[] reg2 = {2,(byte) 163,16,5,5,0,0,0,2,1};
			if (mSamplingRate<=128){
				reg1[0]=0;
				reg2[0]=0;
			} else if (mSamplingRate<=256){
				reg1[0]=1;
				reg2[0]=1;
			}
			else if (mSamplingRate<=512){
				reg1[0]=2;
				reg2[0]=2;
			}
			writeEXGConfiguration(reg1,1);
			writeEXGConfiguration(reg2,2);
		}
		
	}

	
	/**** DISABLE FUNCTIONS *****/
	
	private int disableBit(int number,int disablebitvalue){
		if ((number&disablebitvalue)>0){
			number = number ^ disablebitvalue;
		}
		return number;
	}
	
	//endregion
	
	
	//region --------- MISCELLANEOUS FUNCTIONS ---------
	
	public void reconnect(){
        if (mState==ShimmerBluetooth.STATE_CONNECTED && !mStreaming){
        	String msgReconnect = "Reconnecting the Shimmer...";
			sendStatusMSGtoUI(msgReconnect);
            stop();
            try {
				Thread.sleep(300);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
            connect(mMyBluetoothAddress,"default");
        }
    }

	
	/**
	 * An inquiry is used to request for the current configuration parameters from the Shimmer device (e.g. Accelerometer settings, Configuration Byte, Sampling Rate, Number of Enabled Sensors and Sensors which have been enabled). 
	 */
	public void inquiry() {
		mListofInstructions.add(new byte[]{INQUIRY_COMMAND});
	}
	
	/**
	 * @param enabledSensors This takes in the current list of enabled sensors 
	 * @param sensorToCheck This takes in a single sensor which is to be enabled
	 * @return enabledSensors This returns the new set of enabled sensors, where any sensors which conflicts with sensorToCheck is disabled on the bitmap, so sensorToCheck can be accomodated (e.g. for Shimmer2 using ECG will disable EMG,GSR,..basically any daughter board)
	 *  
	 */
	public int sensorConflictCheckandCorrection(int enabledSensors,int sensorToCheck){

		if (mShimmerVersion==SHIMMER_2R || mShimmerVersion==SHIMMER_2){
			if ((sensorToCheck & SENSOR_GYRO) >0 || (sensorToCheck & SENSOR_MAG) >0){
				enabledSensors = disableBit(enabledSensors,SENSOR_ECG);
				enabledSensors = disableBit(enabledSensors,SENSOR_EMG);
				enabledSensors = disableBit(enabledSensors,SENSOR_GSR);
				enabledSensors = disableBit(enabledSensors,SENSOR_BRIDGE_AMP);
			} else if ((sensorToCheck & SENSOR_BRIDGE_AMP) >0){
				enabledSensors = disableBit(enabledSensors,SENSOR_ECG);
				enabledSensors = disableBit(enabledSensors,SENSOR_EMG);
				enabledSensors = disableBit(enabledSensors,SENSOR_GSR);
				enabledSensors = disableBit(enabledSensors,SENSOR_GYRO);
				enabledSensors = disableBit(enabledSensors,SENSOR_MAG);
			} else if ((sensorToCheck & SENSOR_GSR) >0){
				enabledSensors = disableBit(enabledSensors,SENSOR_ECG);
				enabledSensors = disableBit(enabledSensors,SENSOR_EMG);
				enabledSensors = disableBit(enabledSensors,SENSOR_BRIDGE_AMP);
				enabledSensors = disableBit(enabledSensors,SENSOR_GYRO);
				enabledSensors = disableBit(enabledSensors,SENSOR_MAG);
			} else if ((sensorToCheck & SENSOR_ECG) >0){
				enabledSensors = disableBit(enabledSensors,SENSOR_GSR);
				enabledSensors = disableBit(enabledSensors,SENSOR_EMG);
				enabledSensors = disableBit(enabledSensors,SENSOR_BRIDGE_AMP);
				enabledSensors = disableBit(enabledSensors,SENSOR_GYRO);
				enabledSensors = disableBit(enabledSensors,SENSOR_MAG);
			} else if ((sensorToCheck & SENSOR_EMG) >0){
				enabledSensors = disableBit(enabledSensors,SENSOR_GSR);
				enabledSensors = disableBit(enabledSensors,SENSOR_ECG);
				enabledSensors = disableBit(enabledSensors,SENSOR_BRIDGE_AMP);
				enabledSensors = disableBit(enabledSensors,SENSOR_GYRO);
				enabledSensors = disableBit(enabledSensors,SENSOR_MAG);
			} else if ((sensorToCheck & SENSOR_HEART) >0){
				enabledSensors = disableBit(enabledSensors,SENSOR_EXP_BOARD_A0);
				enabledSensors = disableBit(enabledSensors,SENSOR_EXP_BOARD_A7);
			} else if ((sensorToCheck & SENSOR_EXP_BOARD_A0) >0 || (sensorToCheck & SENSOR_EXP_BOARD_A7) >0){
				enabledSensors = disableBit(enabledSensors,SENSOR_HEART);
				enabledSensors = disableBit(enabledSensors,SENSOR_BATT);
			} else if ((sensorToCheck & SENSOR_BATT) >0){
				enabledSensors = disableBit(enabledSensors,SENSOR_EXP_BOARD_A0);
				enabledSensors = disableBit(enabledSensors,SENSOR_EXP_BOARD_A7);
			}
		}

		else if(mShimmerVersion==SHIMMER_3){
			
			if((sensorToCheck & SENSOR_GSR) >0){
				enabledSensors = disableBit(enabledSensors,SENSOR_INT_ADC_A14);
				enabledSensors = disableBit(enabledSensors,SENSOR_EXG1_16BIT);
				enabledSensors = disableBit(enabledSensors,SENSOR_EXG2_16BIT);
				enabledSensors = disableBit(enabledSensors,SENSOR_EXG1_24BIT);
				enabledSensors = disableBit(enabledSensors,SENSOR_EXG2_24BIT);
				enabledSensors = disableBit(enabledSensors,SENSOR_BRIDGE_AMP);
			} 
			else if((sensorToCheck & SENSOR_EXG1_16BIT) >0 || (sensorToCheck & SENSOR_EXG2_16BIT) >0){
				enabledSensors = disableBit(enabledSensors,SENSOR_INT_ADC_A1);
				enabledSensors = disableBit(enabledSensors,SENSOR_INT_ADC_A12);
				enabledSensors = disableBit(enabledSensors,SENSOR_INT_ADC_A13);
				enabledSensors = disableBit(enabledSensors,SENSOR_INT_ADC_A14);
				enabledSensors = disableBit(enabledSensors,SENSOR_GSR);
				enabledSensors = disableBit(enabledSensors,SENSOR_EXG1_24BIT);
				enabledSensors = disableBit(enabledSensors,SENSOR_EXG2_24BIT);
				enabledSensors = disableBit(enabledSensors,SENSOR_BRIDGE_AMP);
			}
			else if((sensorToCheck & SENSOR_EXG1_24BIT) >0 || (sensorToCheck & SENSOR_EXG2_24BIT) >0){
				enabledSensors = disableBit(enabledSensors,SENSOR_INT_ADC_A1);
				enabledSensors = disableBit(enabledSensors,SENSOR_INT_ADC_A12);
				enabledSensors = disableBit(enabledSensors,SENSOR_INT_ADC_A13);
				enabledSensors = disableBit(enabledSensors,SENSOR_INT_ADC_A14);
				enabledSensors = disableBit(enabledSensors,SENSOR_GSR);
				enabledSensors = disableBit(enabledSensors,SENSOR_EXG1_16BIT);
				enabledSensors = disableBit(enabledSensors,SENSOR_EXG2_16BIT);
				enabledSensors = disableBit(enabledSensors,SENSOR_BRIDGE_AMP);
			}
			else if((sensorToCheck & SENSOR_BRIDGE_AMP) >0){
				enabledSensors = disableBit(enabledSensors,SENSOR_INT_ADC_A12);
				enabledSensors = disableBit(enabledSensors,SENSOR_INT_ADC_A13);
				enabledSensors = disableBit(enabledSensors,SENSOR_INT_ADC_A14);
				enabledSensors = disableBit(enabledSensors,SENSOR_GSR);
				enabledSensors = disableBit(enabledSensors,SENSOR_EXG1_16BIT);
				enabledSensors = disableBit(enabledSensors,SENSOR_EXG2_16BIT);
				enabledSensors = disableBit(enabledSensors,SENSOR_EXG1_24BIT);
				enabledSensors = disableBit(enabledSensors,SENSOR_EXG2_24BIT);
			}
			else if ((sensorToCheck & SENSOR_INT_ADC_A12) >0){
				enabledSensors = disableBit(enabledSensors,SENSOR_EXG1_16BIT);
				enabledSensors = disableBit(enabledSensors,SENSOR_EXG2_16BIT);
				enabledSensors = disableBit(enabledSensors,SENSOR_EXG1_24BIT);
				enabledSensors = disableBit(enabledSensors,SENSOR_EXG2_24BIT);
				enabledSensors = disableBit(enabledSensors,SENSOR_BRIDGE_AMP);
			}
			else if ((sensorToCheck & SENSOR_INT_ADC_A13) >0){
				enabledSensors = disableBit(enabledSensors,SENSOR_EXG1_16BIT);
				enabledSensors = disableBit(enabledSensors,SENSOR_EXG2_16BIT);
				enabledSensors = disableBit(enabledSensors,SENSOR_EXG1_24BIT);
				enabledSensors = disableBit(enabledSensors,SENSOR_EXG2_24BIT);
				enabledSensors = disableBit(enabledSensors,SENSOR_BRIDGE_AMP);
			}
			else if ((sensorToCheck & SENSOR_INT_ADC_A14) >0){
				enabledSensors = disableBit(enabledSensors,SENSOR_GSR);
				enabledSensors = disableBit(enabledSensors,SENSOR_EXG1_16BIT);
				enabledSensors = disableBit(enabledSensors,SENSOR_EXG2_16BIT);
				enabledSensors = disableBit(enabledSensors,SENSOR_EXG1_24BIT);
				enabledSensors = disableBit(enabledSensors,SENSOR_EXG2_24BIT);
				enabledSensors = disableBit(enabledSensors,SENSOR_BRIDGE_AMP);
			}
			
		}
		enabledSensors = enabledSensors ^ sensorToCheck;
		return enabledSensors;
	}
	
	public boolean sensorConflictCheck(int enabledSensors){
		boolean pass=true;
		if (mShimmerVersion != SHIMMER_3){
			if (((enabledSensors & 0xFF)& SENSOR_GYRO) > 0){
				if (((enabledSensors & 0xFF)& SENSOR_EMG) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF)& SENSOR_ECG) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF)& SENSOR_GSR) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF00) & SENSOR_BRIDGE_AMP) > 0){
					pass=false;
				}
			}

			if (((enabledSensors & 0xFF)& SENSOR_MAG) > 0){
				if (((enabledSensors & 0xFF)& SENSOR_EMG) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF)& SENSOR_ECG) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF)& SENSOR_GSR) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF00) & SENSOR_BRIDGE_AMP) > 0){
					pass=false;
				}
			}

			if (((enabledSensors & 0xFF)& SENSOR_EMG) > 0){
				if (((enabledSensors & 0xFF)& SENSOR_GYRO) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF)& SENSOR_MAG) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF)& SENSOR_ECG) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF)& SENSOR_GSR) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF00) & SENSOR_BRIDGE_AMP) > 0){
					pass=false;
				}
			}

			if (((enabledSensors & 0xFF)& SENSOR_ECG) > 0){
				if (((enabledSensors & 0xFF)& SENSOR_GYRO) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF)& SENSOR_MAG) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF)& SENSOR_EMG) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF)& SENSOR_GSR) > 0){
					pass=false;
				}else if (((enabledSensors & 0xFF00) & SENSOR_BRIDGE_AMP) > 0){
					pass=false;
				}
			}

			if (((enabledSensors & 0xFF)& SENSOR_GSR) > 0){
				if (((enabledSensors & 0xFF)& SENSOR_GYRO) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF)& SENSOR_MAG) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF)& SENSOR_EMG) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF)& SENSOR_ECG) > 0){
					pass=false;
				}else if (((enabledSensors & 0xFF00) & SENSOR_BRIDGE_AMP) > 0){
					pass=false;
				}
			}

			if (((enabledSensors & 0xFF00) & SENSOR_BRIDGE_AMP) > 0){
				if (((enabledSensors & 0xFF)& SENSOR_GYRO) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF)& SENSOR_MAG) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF)& SENSOR_EMG) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF)& SENSOR_ECG) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF)& SENSOR_GSR) > 0){
					pass=false;
				} else if (get5VReg()==1){ // if the 5volt reg is set 
					pass=false;
				}
			}

			if (((enabledSensors & 0xFF) & SENSOR_EXP_BOARD_A0) > 0) {
				if (((enabledSensors & 0xFFFFF) & SENSOR_BATT) > 0) {
					pass=false;
				} else if (getPMux()==1){
					
					writePMux(0);
				}
			}

			if (((enabledSensors & 0xFF) & SENSOR_EXP_BOARD_A7) > 0) {
				if (((enabledSensors & 0xFFFFF) & SENSOR_BATT) > 0) {
					pass=false;
				}else if (getPMux()==1){
					writePMux(0);
				}
			}

			if (((enabledSensors & 0xFFFFF) & SENSOR_BATT) > 0) {
				if (((enabledSensors & 0xFF) & SENSOR_EXP_BOARD_A7) > 0){
					pass=false;
				} 
				if (((enabledSensors & 0xFF) & SENSOR_EXP_BOARD_A0) > 0){
					pass=false;
				}
				if (((enabledSensors & 0xFFFFF) & SENSOR_BATT) > 0){
					if (getPMux()==0){
						
						writePMux(1);
					}
				}
			}
			if (!pass){
				
			}
		}
		
		else{
			
			if(((enabledSensors & 0xFF0000) & SENSOR_EXG1_16BIT) > 0 || ((enabledSensors & 0xFF0000) & SENSOR_EXG2_16BIT) > 0){
				
				if (((enabledSensors & 0xFF00) & SENSOR_INT_ADC_A1) > 0){
					pass=false; 
				} else if (((enabledSensors & 0xFF00) & SENSOR_INT_ADC_A12) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF00) & SENSOR_INT_ADC_A13) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF0000) & SENSOR_INT_ADC_A14) > 0){
					pass=false;
				} else if(((enabledSensors & 0xFF) & SENSOR_GSR) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF) & SENSOR_EXG1_24BIT) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF) & SENSOR_EXG2_24BIT) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF00) & SENSOR_BRIDGE_AMP) > 0){
					pass=false;
				}
			}
			
			if(((enabledSensors & 0xFF) & SENSOR_GSR) > 0){
				
				if (((enabledSensors & 0xFF0000) & SENSOR_INT_ADC_A14) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF0000) & SENSOR_EXG1_16BIT) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF0000) & SENSOR_EXG2_16BIT) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF) & SENSOR_EXG1_24BIT) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF) & SENSOR_EXG2_24BIT) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF00) & SENSOR_BRIDGE_AMP) > 0){
					pass=false;
				}
			}
			
			if (((enabledSensors & 0xFF00) & SENSOR_BRIDGE_AMP) > 0){
				  
				if (((enabledSensors & 0xFF00) & SENSOR_INT_ADC_A12) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF00) & SENSOR_INT_ADC_A13) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF0000) & SENSOR_INT_ADC_A14) > 0){
					pass=false;
				} else if(((enabledSensors & 0xFF) & SENSOR_GSR) > 0){
					pass=false;		
				} else if (((enabledSensors & 0xFF0000) & SENSOR_EXG1_16BIT) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF0000) & SENSOR_EXG2_16BIT) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF) & SENSOR_EXG1_24BIT) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF) & SENSOR_EXG2_24BIT) > 0){
					pass=false;
				}
			}
			
			if (((enabledSensors & 0xFF00) & SENSOR_INT_ADC_A12) > 0){
				  
				if (((enabledSensors & 0xFF0000) & SENSOR_EXG1_16BIT) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF0000) & SENSOR_EXG2_16BIT) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF) & SENSOR_EXG1_24BIT) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF) & SENSOR_EXG2_24BIT) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF00) & SENSOR_BRIDGE_AMP) > 0){
					pass=false;
				}
			}
			
			if (((enabledSensors & 0xFF00) & SENSOR_INT_ADC_A13) > 0){
				  
				if (((enabledSensors & 0xFF0000) & SENSOR_EXG1_16BIT) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF0000) & SENSOR_EXG2_16BIT) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF) & SENSOR_EXG1_24BIT) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF) & SENSOR_EXG2_24BIT) > 0){
					pass=false;
				} else if (((enabledSensors & 0xFF00) & SENSOR_BRIDGE_AMP) > 0){
					pass=false;
				}
			}
			
			if (((enabledSensors & 0xFF0000) & SENSOR_INT_ADC_A14) > 0){
				  
				 if(((enabledSensors & 0xFF) & SENSOR_GSR) > 0){
					pass=false;
				 } else if (((enabledSensors & 0xFF0000) & SENSOR_EXG1_16BIT) > 0){
					pass=false;
				 } else if (((enabledSensors & 0xFF0000) & SENSOR_EXG2_16BIT) > 0){
					pass=false;
				 } else if (((enabledSensors & 0xFF) & SENSOR_EXG1_24BIT) > 0){
					pass=false;
				 } else if (((enabledSensors & 0xFF) & SENSOR_EXG2_24BIT) > 0){
					pass=false;
				 } else if (((enabledSensors & 0xFF00) & SENSOR_BRIDGE_AMP) > 0){
					pass=false;
				 }
			}
		}
		
		return pass;
	}
	
	/**
	 * @param enabledSensors this bitmap is only applicable for the instrument driver and does not correspond with the values in the firmware
	 * @return enabledSensorsFirmware returns the bitmap for the firmware
	 * The reason for this is hardware and firmware change may eventually need a different sensor bitmap, to keep the ID forward compatible, this function is used. Therefor the ID can have its own seperate sensor bitmap if needed
	 */
	private int generateSensorBitmapForHardwareControl(int enabledSensors){
		int hardwareSensorBitmap=0;

		//check if the batt volt is enabled (this is only applicable for Shimmer_2R
		if (mShimmerVersion == SHIMMER_2R || mShimmerVersion == SHIMMER_2){
			if (((enabledSensors & 0xFFFFF) & SENSOR_BATT) > 0 ){
				enabledSensors = enabledSensors & 0xFFFF;
				enabledSensors = enabledSensors|SENSOR_EXP_BOARD_A0|SENSOR_EXP_BOARD_A7;
			}
			hardwareSensorBitmap  = enabledSensors;
		} else if (mShimmerVersion == SHIMMER_3){
			if (((enabledSensors & 0xFF)& SENSOR_ACCEL) > 0){
				hardwareSensorBitmap = hardwareSensorBitmap|Configuration.Shimmer3.SensorBitmap.SENSOR_A_ACCEL_S3;
			}
			if ((enabledSensors & SENSOR_DACCEL) > 0){
				hardwareSensorBitmap = hardwareSensorBitmap|Configuration.Shimmer3.SensorBitmap.SENSOR_D_ACCEL_S3;
			}
			if (((enabledSensors & 0xFF)& SENSOR_EXG1_24BIT) > 0){
				hardwareSensorBitmap = hardwareSensorBitmap|Configuration.Shimmer3.SensorBitmap.SENSOR_EXG1_24BIT;
			}
			if (((enabledSensors & 0xFF)& SENSOR_EXG2_24BIT) > 0){
				hardwareSensorBitmap = hardwareSensorBitmap|Configuration.Shimmer3.SensorBitmap.SENSOR_EXG2_24BIT;
			}

			if ((enabledSensors& SENSOR_EXG1_16BIT) > 0){
				hardwareSensorBitmap = hardwareSensorBitmap|Configuration.Shimmer3.SensorBitmap.SENSOR_EXG1_16BIT;
			}
			if ((enabledSensors & SENSOR_EXG2_16BIT) > 0){
				hardwareSensorBitmap = hardwareSensorBitmap|Configuration.Shimmer3.SensorBitmap.SENSOR_EXG2_16BIT;
			}
			if (((enabledSensors & 0xFF)& SENSOR_GYRO) > 0){
				hardwareSensorBitmap = hardwareSensorBitmap|Configuration.Shimmer3.SensorBitmap.SENSOR_GYRO_S3;
			}
			if (((enabledSensors & 0xFF)& SENSOR_MAG) > 0){
				hardwareSensorBitmap = hardwareSensorBitmap|Configuration.Shimmer3.SensorBitmap.SENSOR_MAG_S3;
			}
			if ((enabledSensors & SENSOR_BATT) > 0){
				hardwareSensorBitmap = hardwareSensorBitmap|Configuration.Shimmer3.SensorBitmap.SENSOR_VBATT_S3;
			}
			if ((enabledSensors & SENSOR_EXT_ADC_A7) > 0){
				hardwareSensorBitmap = hardwareSensorBitmap|Configuration.Shimmer3.SensorBitmap.SENSOR_EXT_A7;
			}
			if ((enabledSensors & SENSOR_EXT_ADC_A6) > 0){
				hardwareSensorBitmap = hardwareSensorBitmap|Configuration.Shimmer3.SensorBitmap.SENSOR_EXT_A6;
			}
			if ((enabledSensors & SENSOR_EXT_ADC_A15) > 0){
				hardwareSensorBitmap = hardwareSensorBitmap|Configuration.Shimmer3.SensorBitmap.SENSOR_EXT_A15;
			}
			if ((enabledSensors & SENSOR_INT_ADC_A1) > 0){
				hardwareSensorBitmap = hardwareSensorBitmap|Configuration.Shimmer3.SensorBitmap.SENSOR_INT_A1;
			}
			if ((enabledSensors & SENSOR_INT_ADC_A12) > 0){
				hardwareSensorBitmap = hardwareSensorBitmap|Configuration.Shimmer3.SensorBitmap.SENSOR_INT_A12;
			}
			if ((enabledSensors & SENSOR_INT_ADC_A13) > 0){
				hardwareSensorBitmap = hardwareSensorBitmap|Configuration.Shimmer3.SensorBitmap.SENSOR_INT_A13;
			}
			if ((enabledSensors & SENSOR_INT_ADC_A14) > 0){
				hardwareSensorBitmap = hardwareSensorBitmap|Configuration.Shimmer3.SensorBitmap.SENSOR_INT_A14;
			}
			if  ((enabledSensors & SENSOR_BMP180) > 0){
				hardwareSensorBitmap = hardwareSensorBitmap|Configuration.Shimmer3.SensorBitmap.SENSOR_BMP180;
			} 
			if ((enabledSensors & SENSOR_GSR) > 0){
				hardwareSensorBitmap = hardwareSensorBitmap|Configuration.Shimmer3.SensorBitmap.SENSOR_GSR;
			}
			if ((enabledSensors & SENSOR_BRIDGE_AMP) > 0){
				hardwareSensorBitmap = hardwareSensorBitmap|Configuration.Shimmer3.SensorBitmap.SENSOR_BRIDGE_AMP;
			} 
		} else { 
			hardwareSensorBitmap  = enabledSensors;
		}

		return hardwareSensorBitmap;
	}

	public void toggleLed() {
		mListofInstructions.add(new byte[]{TOGGLE_LED_COMMAND});
	}
	
	@Override
	protected void checkBattery(){
		if (mStreaming ){
			if(mShimmerVersion == SHIMMER_3 && mFWIdentifier==3){
				if (!mWaitForAck) {	
					if (mVSenseBattMA.getMean()<mLowBattLimit*1000*0.8) {
						if (mCurrentLEDStatus!=2) {
							writeLEDCommand(2);
						}
					} else if (mVSenseBattMA.getMean()<mLowBattLimit*1000) {
						if (mCurrentLEDStatus!=1) {
							writeLEDCommand(1);
						}
					} else if(mVSenseBattMA.getMean()>mLowBattLimit*1000+100) { //+100 is to make sure the limits are different to prevent excessive switching when the batt value is at the threshold
						if (mCurrentLEDStatus!=0) {
							writeLEDCommand(0);
						}
					}

				}
			}
			if(mShimmerVersion == SHIMMER_2R){
				if (!mWaitForAck) {	
					if (mVSenseBattMA.getMean()<mLowBattLimit*1000) {
						if (mCurrentLEDStatus!=1) {
							writeLEDCommand(1);
						}
					} else if(mVSenseBattMA.getMean()>mLowBattLimit*1000+100) { //+100 is to make sure the limits are different to prevent excessive switching when the batt value is at the threshold
						if (mCurrentLEDStatus!=0) {
							writeLEDCommand(0);
						}
					}

				}
			}
			

		
		}
	
	}
	
	public void resetCalibratedTimeStamp(){
		mLastReceivedCalibratedTimeStamp = -1;
		mFirstTimeCalTime = true;
		mCurrentTimeStampCycle = 0;
	}
	
	//endregion
	
	

}
