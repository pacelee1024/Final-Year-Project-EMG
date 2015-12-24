//Rev_1.8
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
 * @author Jong Chern Lim, Ruaidhri Molloy, Alejandro Saez 
 * 
 *  * Changes since 1.7
 * - remove multiplying factor (2.8) from the gain in the calculation of the Bridge Amplifier calibrated data
 * 
 * @date   September, 2014
 * 
 * Changes since 1.6
 * - Added functionality for plotmanager see MSSAPI , addExtraSignalProperty, removeExtraSignalProperty ,getListofEnabledSensorSignalsandFormats()
 * - various exg advance updates
 * 
 * @date   July, 2014
 * 
 * Changes since 1.5
 * - Bridge Amplifier gauge support for Shimmer3
 * - Bug fix for strain gauge calibration for Shimmer2r
 * - Enable 3D orientation for wide range accel, orientation algorithm defaults to low noise even if wide range is enabled
 * - Fixed quaternion naming typo
 * - Commented out initialisation mSensorBitmaptoName
 * - add method getPressureRawCoefficients
 *  
 * @date   October, 2013
 * 
 * Changes since 1.4
 * - fix getListofEnabledSensors, which was not returning accel shimmer2r
 * - fix null pointer graddes algo when using Shimmer2
 * - converted to abstract class , and added checkbattery abstract method for the Shimmer2r
 * - updated gsr calibrate command parameters for Shimmer3
 * - removed mSamplingRate decimal formatter, decimal formatter should be done on the UI
 * - fixed a GSR Shimmer2 problem when using autorange
 * - added VSense Batt and VSense Reg and Timestamp to getListOfEnabledSensorSignals
 * - added getSamplingRate()
 * - added get methods for calibration parameters accel,gyro,mag,accel2
 * - updated getoffsetaccel
 * - added exg configurations
 * - added i24r for exg
 * - add get exg configurations
 * - renamed i16* to i16r for consistency
 * - added EXG_CHIP1 = 0 and EXG_CHIP2=1
 * - updated Mag Default Range
 * 
 */

package com.shimmerresearch.driver;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.vecmath.Quat4d;
import javax.vecmath.Vector3d;

import org.apache.commons.math.stat.descriptive.DescriptiveStatistics;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.shimmerresearch.algorithms.GradDes3DOrientation;
import com.shimmerresearch.algorithms.GradDes3DOrientation.Quaternion;
import com.sun.org.apache.bcel.internal.generic.ISUB;

public abstract class ShimmerObject {

	public static final int SHIMMER_1=0;
	public static final int SHIMMER_2=1;
	public static final int SHIMMER_2R=2;
	public static final int SHIMMER_3=3;
	public static final int SHIMMER_SR30=4;

	//Sensor Bitmap for ID ; for the purpose of forward compatibility the sensor bitmap and the ID and the sensor bitmap for the Shimmer firmware has been kept separate, 
	public static final int SENSOR_ACCEL				   = 0x80; 
	public static final int SENSOR_DACCEL				   = 0x1000; //only 
	public static final int SENSOR_GYRO				   	   = 0x40;
	public static final int SENSOR_MAG					   = 0x20;
	public static final int SENSOR_ECG					   = 0x10;
	public static final int SENSOR_EMG					   = 0x08;
	public static final int SENSOR_EXG1_24BIT			   = 0x10; //only applicable for Shimmer3
	public static final int SENSOR_EXG2_24BIT			   = 0x08; //only applicable for Shimmer3
	public static final int SHIMMER3_SENSOR_ECG			   = SENSOR_EXG1_24BIT + SENSOR_EXG2_24BIT;
	public static final int SHIMMER3_SENSOR_EMG			   = SENSOR_EXG1_24BIT;
	public static final int SENSOR_GSR					   = 0x04;
	public static final int SENSOR_EXP_BOARD_A7		       = 0x02; //only Applicable for Shimmer2
	public static final int SENSOR_EXP_BOARD_A0		       = 0x01; //only Applicable for Shimmer2
	public static final int SENSOR_EXP_BOARD		       = SENSOR_EXP_BOARD_A7+SENSOR_EXP_BOARD_A0;
	public static final int SENSOR_BRIDGE_AMP			   = 0x8000;
	public static final int SENSOR_HEART				   = 0x4000;
	public static final int SENSOR_BATT	  			       = 0x2000; //THIS IS A DUMMY VALUE
	public static final int SENSOR_EXT_ADC_A7              = 0x02; //only Applicable for Shimmer3
	public static final int SENSOR_EXT_ADC_A6              = 0x01; //only Applicable for Shimmer3
	public static final int SENSOR_EXT_ADC_A15             = 0x0800;
	public static final int SENSOR_INT_ADC_A1              = 0x0400;
	public static final int SENSOR_INT_ADC_A12             = 0x0200;
	public static final int SENSOR_INT_ADC_A13             = 0x0100;
	public static final int SENSOR_INT_ADC_A14             = 0x800000;
	public static final int SENSOR_ALL_ADC_SHIMMER3        = SENSOR_INT_ADC_A14 | SENSOR_INT_ADC_A13 | SENSOR_INT_ADC_A12 | SENSOR_INT_ADC_A1 | SENSOR_EXT_ADC_A7 | SENSOR_EXT_ADC_A6 | SENSOR_EXT_ADC_A15; 
	public static final int SENSOR_BMP180              	   = 0x40000;
	public static final int SENSOR_EXG1_16BIT			   = 0x100000; //only applicable for Shimmer3
	public static final int SENSOR_EXG2_16BIT			   = 0x080000; //only applicable for Shimmer3
	public BiMap<String, String> mSensorBitmaptoName;  
	/*
		{  
		final Map<String, String> tempSensorBMtoName = new HashMap<String, String>();  
		tempSensorBMtoName.put(Integer.toString(SENSOR_ACCEL), "Accelerometer");  
		tempSensorBMtoName.put(Integer.toString(SENSOR_GYRO), "Gyroscope");  
		tempSensorBMtoName.put(Integer.toString(SENSOR_MAG), "Magnetometer");  
		tempSensorBMtoName.put(Integer.toString(SENSOR_ECG), "ECG");  
		tempSensorBMtoName.put(Integer.toString(SENSOR_EMG), "EMG");  
		tempSensorBMtoName.put(Integer.toString(SENSOR_GSR), "GSR");  
		tempSensorBMtoName.put(Integer.toString(SENSOR_EXP_BOARD_A7), "Exp Board A7");
		tempSensorBMtoName.put(Integer.toString(SENSOR_EXP_BOARD_A0), "Exp Board A0");
		tempSensorBMtoName.put(Integer.toString(SENSOR_EXP_BOARD), "Exp Board");
		tempSensorBMtoName.put(Integer.toString(SENSOR_BRIDGE_AMP), "Bridge Amplifier");
		tempSensorBMtoName.put(Integer.toString(SENSOR_HEART), "Heart Rate");  
		tempSensorBMtoName.put(Integer.toString(SENSOR_BATT), "Battery Voltage");
		tempSensorBMtoName.put(Integer.toString(SENSOR_EXT_ADC_A7), "External ADC A7");  
		tempSensorBMtoName.put(Integer.toString(SENSOR_EXT_ADC_A6), "External ADC A6");  
		tempSensorBMtoName.put(Integer.toString(SENSOR_EXT_ADC_A15), "External ADC A15");
		tempSensorBMtoName.put(Integer.toString(SENSOR_INT_ADC_A1), "Internal ADC A1");
		tempSensorBMtoName.put(Integer.toString(SENSOR_INT_ADC_A12), "Internal ADC A12");
		tempSensorBMtoName.put(Integer.toString(SENSOR_INT_ADC_A13), "Internal ADC A13");
		tempSensorBMtoName.put(Integer.toString(SENSOR_INT_ADC_A14), "Internal ADC A14");
		mSensorBitmaptoName = ImmutableBiMap.copyOf(Collections.unmodifiableMap(tempSensorBMtoName));
		}  
	 */

	//Constants describing the packet type
	public static final byte DATA_PACKET                      		= (byte) 0x00;
	public static final byte INQUIRY_COMMAND                  		= (byte) 0x01;
	public static final byte INQUIRY_RESPONSE                 		= (byte) 0x02;
	public static final byte GET_SAMPLING_RATE_COMMAND 	   			= (byte) 0x03;
	public static final byte SAMPLING_RATE_RESPONSE           		= (byte) 0x04;
	public static final byte SET_SAMPLING_RATE_COMMAND        		= (byte) 0x05;
	public static final byte TOGGLE_LED_COMMAND              		= (byte) 0x06;
	public static final byte START_STREAMING_COMMAND          		= (byte) 0x07;
	public static final byte SET_SENSORS_COMMAND              		= (byte) 0x08;
	public static final byte SET_ACCEL_SENSITIVITY_COMMAND    		= (byte) 0x09;
	public static final byte ACCEL_SENSITIVITY_RESPONSE       		= (byte) 0x0A;
	public static final byte GET_ACCEL_SENSITIVITY_COMMAND    		= (byte) 0x0B;
	public static final byte SET_5V_REGULATOR_COMMAND         		= (byte) 0x0C; // only Shimmer 2
	public static final byte SET_PMUX_COMMAND                 		= (byte) 0x0D; // only Shimmer 2
	public static final byte SET_CONFIG_BYTE0_COMMAND   	   		= (byte) 0x0E;
	public static final byte CONFIG_BYTE0_RESPONSE      	   		= (byte) 0x0F;
	public static final byte GET_CONFIG_BYTE0_COMMAND   	   		= (byte) 0x10;
	public static final byte STOP_STREAMING_COMMAND           		= (byte) 0x20;
	public static final byte SET_ACCEL_CALIBRATION_COMMAND			= (byte) 0x11;
	public static final byte ACCEL_CALIBRATION_RESPONSE       		= (byte) 0x12;
	public static final byte LSM303DLHC_ACCEL_CALIBRATION_RESPONSE 	= (byte) 0x1B;
	public static final byte SET_LSM303DLHC_ACCEL_CALIBRATION_COMMAND = (byte) 0x1A;
	public static final byte GET_ACCEL_CALIBRATION_COMMAND    		= (byte) 0x13;
	public static final byte SET_GYRO_CALIBRATION_COMMAND 	  		= (byte) 0x14;
	public static final byte GYRO_CALIBRATION_RESPONSE        		= (byte) 0x15;
	public static final byte GET_GYRO_CALIBRATION_COMMAND     		= (byte) 0x16;
	public static final byte SET_MAG_CALIBRATION_COMMAND      		= (byte) 0x17;
	public static final byte MAG_CALIBRATION_RESPONSE         		= (byte) 0x18;
	public static final byte GET_MAG_CALIBRATION_COMMAND      		= (byte) 0x19;
	public static final byte SET_GSR_RANGE_COMMAND			   		= (byte) 0x21;
	public static final byte GSR_RANGE_RESPONSE			   			= (byte) 0x22;
	public static final byte GET_GSR_RANGE_COMMAND			   		= (byte) 0x23;
	public static final byte GET_SHIMMER_VERSION_COMMAND      		= (byte) 0x24;
	public static final byte GET_SHIMMER_VERSION_COMMAND_NEW      	= (byte) 0x3F; //this is to avoid the $ char which is used by rn42
	public static final byte GET_SHIMMER_VERSION_RESPONSE     		= (byte) 0x25;
	public static final byte SET_EMG_CALIBRATION_COMMAND      		= (byte) 0x26;
	public static final byte EMG_CALIBRATION_RESPONSE         		= (byte) 0x27;
	public static final byte GET_EMG_CALIBRATION_COMMAND      		= (byte) 0x28;
	public static final byte SET_ECG_CALIBRATION_COMMAND      		= (byte) 0x29;
	public static final byte ECG_CALIBRATION_RESPONSE         		= (byte) 0x2A;
	public static final byte GET_ECG_CALIBRATION_COMMAND      		= (byte) 0x2B;
	public static final byte GET_ALL_CALIBRATION_COMMAND      		= (byte) 0x2C;
	public static final byte ALL_CALIBRATION_RESPONSE         		= (byte) 0x2D; 
	public static final byte GET_FW_VERSION_COMMAND          		= (byte) 0x2E;
	public static final byte FW_VERSION_RESPONSE             	 	= (byte) 0x2F;
	public static final byte SET_BLINK_LED                    		= (byte) 0x30;
	public static final byte BLINK_LED_RESPONSE               		= (byte) 0x31;
	public static final byte GET_BLINK_LED                    		= (byte) 0x32;
	public static final byte SET_GYRO_TEMP_VREF_COMMAND       		= (byte) 0x33;
	public static final byte SET_BUFFER_SIZE_COMMAND          		= (byte) 0x34;
	public static final byte BUFFER_SIZE_RESPONSE             		= (byte) 0x35;
	public static final byte GET_BUFFER_SIZE_COMMAND          		= (byte) 0x36;
	public static final byte SET_MAG_GAIN_COMMAND             		= (byte) 0x37;
	public static final byte MAG_GAIN_RESPONSE                		= (byte) 0x38;
	public static final byte GET_MAG_GAIN_COMMAND             		= (byte) 0x39;
	public static final byte SET_MAG_SAMPLING_RATE_COMMAND    		= (byte) 0x3A;
	public static final byte MAG_SAMPLING_RATE_RESPONSE       		= (byte) 0x3B;
	public static final byte GET_MAG_SAMPLING_RATE_COMMAND    		= (byte) 0x3C;
	public static final byte SET_ACCEL_SAMPLING_RATE_COMMAND  		= (byte) 0x40;
	public static final byte ACCEL_SAMPLING_RATE_RESPONSE  			= (byte) 0x41;
	public static final byte GET_ACCEL_SAMPLING_RATE_COMMAND  		= (byte) 0x42;
	public static final byte SET_LSM303DLHC_ACCEL_LPMODE_COMMAND 	= (byte) 0x43;
	public static final byte LSM303DLHC_ACCEL_LPMODE_RESPONSE		= (byte) 0x44;
	public static final byte GET_LSM303DLHC_ACCEL_LPMODE_COMMAND 	= (byte) 0x45;
	public static final byte SET_LSM303DLHC_ACCEL_HRMODE_COMMAND	= (byte) 0x46;
	public static final byte LSM303DLHC_ACCEL_HRMODE_RESPONSE		= (byte) 0x47;
	public static final byte GET_LSM303DLHC_ACCEL_HRMODE_COMMAND 	= (byte) 0x48;
	public static final byte SET_MPU9150_GYRO_RANGE_COMMAND 		= (byte) 0x49;
	public static final byte MPU9150_GYRO_RANGE_RESPONSE 			= (byte) 0x4A;
	public static final byte GET_MPU9150_GYRO_RANGE_COMMAND 		= (byte) 0x4B;
	public static final byte SET_MPU9150_SAMPLING_RATE_COMMAND 		= (byte) 0x4C;
	public static final byte MPU9150_SAMPLING_RATE_RESPONSE 		= (byte) 0x4D;
	public static final byte GET_MPU9150_SAMPLING_RATE_COMMAND 		= (byte) 0x4E;
	public static final byte SET_BMP180_PRES_RESOLUTION_COMMAND 	= (byte) 0x52;
	public static final byte BMP180_PRES_RESOLUTION_RESPONSE 		= (byte) 0x53;
	public static final byte GET_BMP180_PRES_RESOLUTION_COMMAND 	= (byte) 0x54;
	public static final byte SET_BMP180_PRES_CALIBRATION_COMMAND	= (byte) 0x55;
	public static final byte BMP180_PRES_CALIBRATION_RESPONSE 		= (byte) 0x56;
	public static final byte GET_BMP180_PRES_CALIBRATION_COMMAND 	= (byte) 0x57;
	public static final byte BMP180_CALIBRATION_COEFFICIENTS_RESPONSE = (byte) 0x58;
	public static final byte GET_BMP180_CALIBRATION_COEFFICIENTS_COMMAND = (byte) 0x59;
	public static final byte RESET_TO_DEFAULT_CONFIGURATION_COMMAND = (byte) 0x5A;
	public static final byte RESET_CALIBRATION_VALUE_COMMAND 		= (byte) 0x5B;
	public static final byte MPU9150_MAG_SENS_ADJ_VALS_RESPONSE 	= (byte) 0x5C;
	public static final byte GET_MPU9150_MAG_SENS_ADJ_VALS_COMMAND 	= (byte) 0x5D;
	public static final byte SET_INTERNAL_EXP_POWER_ENABLE_COMMAND 	= (byte) 0x5E;
	public static final byte INTERNAL_EXP_POWER_ENABLE_RESPONSE 	= (byte) 0x5F;
	public static final byte GET_INTERNAL_EXP_POWER_ENABLE_COMMAND 	= (byte) 0x60;
	public static final byte SET_EXG_REGS_COMMAND 					= (byte) 0x61;
	public static final byte EXG_REGS_RESPONSE 						= (byte) 0x62;
	public static final byte GET_EXG_REGS_COMMAND 					= (byte) 0x63;
	public static final byte DAUGHTER_CARD_ID_RESPONSE 				= (byte) 0x65;
	public static final byte GET_DAUGHTER_CARD_ID_COMMAND			= (byte) 0x66;
	public static final byte SET_BAUD_RATE_COMMAND 					= (byte) 0x6A;
	public static final byte BAUD_RATE_RESPONSE 					= (byte) 0x6B;
	public static final byte GET_BAUD_RATE_COMMAND 					= (byte) 0x6C;
	public static final byte START_SDBT_COMMAND 					= (byte) 0x70;
	public static final byte STATUS_RESPONSE	 					= (byte) 0x71;
	public static final byte GET_STATUS_COMMAND 					= (byte) 0x72;
	public static final byte DIR_RESPONSE		 					= (byte) 0x88;
	public static final byte GET_DIR_COMMAND 						= (byte) 0x89;
	public static final byte INSTREAM_CMD_RESPONSE 					= (byte) 0x8A;
	public static final byte ACK_COMMAND_PROCESSED            		= (byte) 0xff;

	protected String mMyName;														// This stores the user assigned name
	protected String mMyBluetoothAddress="";
	public static final int MAX_NUMBER_OF_SIGNALS = 40; //used to be 11 but now 13 because of the SR30 + 8 for 3d orientation
	public static final int MAX_INQUIRY_PACKET_SIZE = 47;
	protected int mFWCode=0;
	//		protected double mFWVersion;
	protected int mFWMajorVersion;
	protected int mFWMinorVersion;
	protected int mFWInternal;
	protected double mFWIdentifier;
	protected String mFWVersionFullName="";
	protected String mClassName="Shimmer";
	private double mLastReceivedTimeStamp=0;
	protected double mCurrentTimeStampCycle=0;
	protected double mSamplingRate; 	                                        	// 51.2Hz is the default sampling rate 
	protected int mEnabledSensors;												// This stores the enabled sensors


	protected int mPacketSize=0; 													// Default 2 bytes for time stamp and 6 bytes for accelerometer 
	protected int mAccelRange=0;													// This stores the current accelerometer range being used. The accelerometer range is stored during two instances, once an ack packet is received after a writeAccelRange(), and after a response packet has been received after readAccelRange()  	
	protected int mMagSamplingRate=4;												// This stores the current Mag Sampling rate, it is a value between 0 and 6; 0 = 0.5 Hz; 1 = 1.0 Hz; 2 = 2.0 Hz; 3 = 5.0 Hz; 4 = 10.0 Hz; 5 = 20.0 Hz; 6 = 50.0 Hz
	protected int mAccelSamplingRate=0;
	protected int mMPU9150SamplingRate=0;
	protected int mMagGain=1;														// Currently not supported on Shimmer2. This stores the current Mag Range, it is a value between 0 and 6; 0 = 0.7 Ga; 1 = 1.0 Ga; 2 = 1.5 Ga; 3 = 2.0 Ga; 4 = 3.2 Ga; 5 = 3.8 Ga; 6 = 4.5 Ga
	protected int mGyroRange=1;													// This stores the current Mag Range, it is a value between 0 and 6; 0 = 0.7 Ga; 1 = 1.0 Ga; 2 = 1.5 Ga; 3 = 2.0 Ga; 4 = 3.2 Ga; 5 = 3.8 Ga; 6 = 4.5 Ga
	protected int mGSRRange=4;													// This stores the current GSR range being used.
	protected int mInternalExpPower=-1;													// This shows whether the internal exp power is enabled.
	protected long mConfigByte0;
	protected int mNChannels=0;	                                                // Default number of sensor channels set to three because of the on board accelerometer 
	protected int mBufferSize;                   							
	protected int mShimmerVersion=-1;
	private String[] mSignalNameArray=new String[MAX_NUMBER_OF_SIGNALS];							// 19 is the maximum number of signal thus far
	protected String[] mSignalDataTypeArray=new String[MAX_NUMBER_OF_SIGNALS];						// 19 is the maximum number of signal thus far
	protected boolean mDefaultCalibrationParametersECG = true;
	protected boolean mDefaultCalibrationParametersEMG = true;
	protected boolean mDefaultCalibrationParametersAccel = true;
	protected boolean mDefaultCalibrationParametersDigitalAccel = true; //Also known as the wide range accelerometer
	protected int mPressureResolution;
	//
	protected double[][] AlignmentMatrixAccel = {{-1,0,0},{0,-1,0},{0,0,1}}; 			
	protected double[][] SensitivityMatrixAccel = {{38,0,0},{0,38,0},{0,0,38}}; 	
	protected double[][] OffsetVectorAccel = {{2048},{2048},{2048}};				
	protected abstract void checkBattery();
	protected double[][] AlignmentMatrixAccel2 = {{-1,0,0},{0,1,0},{0,0,-1}}; 			
	protected double[][] SensitivityMatrixAccel2 = {{1631,0,0},{0,1631,0},{0,0,1631}}; 	
	protected double[][] OffsetVectorAccel2 = {{0},{0},{0}};	

	//Default values Shimmer2 
	protected static double[][] SensitivityMatrixAccel1p5gShimmer2 = {{101,0,0},{0,101,0},{0,0,101}};
	protected static double[][] SensitivityMatrixAccel2gShimmer2 = {{76,0,0},{0,76,0},{0,0,76}};
	protected static double[][] SensitivityMatrixAccel4gShimmer2 = {{38,0,0},{0,38,0},{0,0,38}};
	protected static double[][] SensitivityMatrixAccel6gShimmer2 = {{25,0,0},{0,25,0},{0,0,25}};
	protected static double[][] AlignmentMatrixAccelShimmer2 =  {{-1,0,0},{0,-1,0},{0,0,1}}; 			
	protected static double[][] OffsetVectorAccelShimmer2 = {{2048},{2048},{2048}};			
	//Shimmer3
	protected static double[][] SensitivityMatrixLowNoiseAccel2gShimmer3 = {{83,0,0},{0,83,0},{0,0,83}};
	protected static double[][] SensitivityMatrixWideRangeAccel2gShimmer3 = {{1631,0,0},{0,1631,0},{0,0,1631}};
	protected static double[][] SensitivityMatrixWideRangeAccel4gShimmer3 = {{815,0,0},{0,815,0},{0,0,815}};
	protected static double[][] SensitivityMatrixWideRangeAccel8gShimmer3 = {{408,0,0},{0,408,0},{0,0,408}};
	protected static double[][] SensitivityMatrixWideRangeAccel16gShimmer3 = {{135,0,0},{0,135,0},{0,0,135}};
	protected static double[][] AlignmentMatrixLowNoiseAccelShimmer3 = {{0,-1,0},{-1,0,0},{0,0,-1}};

	protected static double[][] AlignmentMatrixWideRangeAccelShimmer3 = {{-1,0,0},{0,1,0},{0,0,-1}}; 	
	protected static double[][] AlignmentMatrixAccelShimmer3 = {{0,-1,0},{-1,0,0},{0,0,-1}};
	protected static double[][] OffsetVectorLowNoiseAccelShimmer3 = {{2047},{2047},{2047}};
	protected static double[][] OffsetVectorWideRangeAccelShimmer3 = {{0},{0},{0}};

	protected double OffsetECGRALL=2060;
	protected double GainECGRALL=175;
	protected double OffsetECGLALL=2060;
	protected double GainECGLALL=175;
	protected double OffsetEMG=2060;
	protected double GainEMG=750;

	protected boolean mDefaultCalibrationParametersGyro = true;
	protected double[][] AlignmentMatrixGyro = {{0,-1,0},{-1,0,0},{0,0,-1}}; 				
	protected double[][] SensitivityMatrixGyro = {{2.73,0,0},{0,2.73,0},{0,0,2.73}}; 		
	protected double[][] OffsetVectorGyro = {{1843},{1843},{1843}};						

	//Default values Shimmer2
	protected static double[][] AlignmentMatrixGyroShimmer2 = {{0,-1,0},{-1,0,0},{0,0,-1}}; 				
	protected static double[][] SensitivityMatrixGyroShimmer2 = {{2.73,0,0},{0,2.73,0},{0,0,2.73}}; 		
	protected static double[][] OffsetVectorGyroShimmer2 = {{1843},{1843},{1843}};
	//Shimmer3
	protected static double[][] SensitivityMatrixGyro250dpsShimmer3 = {{131,0,0},{0,131,0},{0,0,131}};
	protected static double[][] SensitivityMatrixGyro500dpsShimmer3 = {{65.5,0,0},{0,65.5,0},{0,0,65.5}};
	protected static double[][] SensitivityMatrixGyro1000dpsShimmer3 = {{32.8,0,0},{0,32.8,0},{0,0,32.8}};
	protected static double[][] SensitivityMatrixGyro2000dpsShimmer3 = {{16.4,0,0},{0,16.4,0},{0,0,16.4}};
	protected static double[][] AlignmentMatrixGyroShimmer3 = {{0,-1,0},{-1,0,0},{0,0,-1}}; 				
	protected static double[][] OffsetVectorGyroShimmer3 = {{0},{0},{0}};		

	protected int mCurrentLEDStatus=0;
	protected boolean mDefaultCalibrationParametersMag = true;
	protected double[][] AlignmentMatrixMag = {{1,0,0},{0,1,0},{0,0,-1}}; 				
	protected double[][] SensitivityMatrixMag = {{580,0,0},{0,580,0},{0,0,580}}; 		
	protected double[][] OffsetVectorMag = {{0},{0},{0}};								

	//Default values Shimmer2 and Shimmer3
	protected static double[][] AlignmentMatrixMagShimmer2 = {{1,0,0},{0,1,0},{0,0,-1}};
	protected static double[][] SensitivityMatrixMagShimmer2 = {{580,0,0},{0,580,0},{0,0,580}}; 		
	protected static double[][] OffsetVectorMagShimmer2 = {{0},{0},{0}};				
	//Shimmer3
	protected static double[][] AlignmentMatrixMagShimmer3 = {{-1,0,0},{0,1,0},{0,0,-1}}; 				
	protected static double[][] SensitivityMatrixMagShimmer3 = {{1100,0,0},{0,1100,0},{0,0,980}}; 		
	protected static double[][] OffsetVectorMagShimmer3 = {{0},{0},{0}};		

	protected static double[][] SensitivityMatrixMag1p3GaShimmer3 = {{1100,0,0},{0,1100,0},{0,0,980}};
	protected static double[][] SensitivityMatrixMag1p9GaShimmer3 = {{855,0,0},{0,855,0},{0,0,760}};
	protected static double[][] SensitivityMatrixMag2p5GaShimmer3 = {{670,0,0},{0,670,0},{0,0,600}};
	protected static double[][] SensitivityMatrixMag4GaShimmer3 = {{450,0,0},{0,450,0},{0,0,400}};
	protected static double[][] SensitivityMatrixMag4p7GaShimmer3 = {{400,0,0},{0,400,0},{0,0,355}};
	protected static double[][] SensitivityMatrixMag5p6GaShimmer3 = {{330,0,0},{0,330,0},{0,0,295}};
	protected static double[][] SensitivityMatrixMag8p1GaShimmer3 = {{230,0,0},{0,230,0},{0,0,205}};

	protected static double[][] SensitivityMatrixMag0p8GaShimmer2 = {{1370,0,0},{0,1370,0},{0,0,1370}};
	protected static double[][] SensitivityMatrixMag1p3GaShimmer2 = {{1090,0,0},{0,1090,0},{0,0,1090}};
	protected static double[][] SensitivityMatrixMag1p9GaShimmer2 = {{820,0,0},{0,820,0},{0,0,820}};
	protected static double[][] SensitivityMatrixMag2p5GaShimmer2 = {{660,0,0},{0,660,0},{0,0,660}};
	protected static double[][] SensitivityMatrixMag4p0GaShimmer2 = {{440,0,0},{0,440,0},{0,0,440}};
	protected static double[][] SensitivityMatrixMag4p7GaShimmer2 = {{390,0,0},{0,390,0},{0,0,390}};
	protected static double[][] SensitivityMatrixMag5p6GaShimmer2 = {{330,0,0},{0,330,0},{0,0,330}};
	protected static double[][] SensitivityMatrixMag8p1GaShimmer2 = {{230,0,0},{0,230,0},{0,0,230}};


	protected double AC1 = 408;
	protected double AC2 = -72;
	protected double AC3 = -14383;
	protected double AC4 = 332741;
	protected double AC5 = 32757;
	protected double AC6 = 23153;
	protected double B1 = 6190;
	protected double B2 = 4;
	protected double MB = -32767;
	protected double MC = -8711;
	protected double MD = 2868;

	protected boolean mLowPowerMag = false;
	protected boolean mLowPowerAccel = false;
	protected boolean mLowPowerGyro = false;
	protected long mPacketLossCount=0;
	protected double mPacketReceptionRate=100;
	protected double mLastReceivedCalibratedTimeStamp=-1; 
	protected boolean mFirstTimeCalTime=true;
	protected double mCalTimeStart;
	private double mLastKnownHeartRate=0;
	protected DescriptiveStatistics mVSenseBattMA= new DescriptiveStatistics(1024);
	Quat4d mQ = new Quat4d();
	GradDes3DOrientation mOrientationAlgo;
	protected boolean mOrientationEnabled = false;
	protected boolean mEnableOntheFlyGyroOVCal = false;

	protected double mGyroOVCalThreshold = 1.2;
	DescriptiveStatistics mGyroX;
	DescriptiveStatistics mGyroY;
	DescriptiveStatistics mGyroZ;
	DescriptiveStatistics mGyroXRaw;
	DescriptiveStatistics mGyroYRaw;
	DescriptiveStatistics mGyroZRaw;
	protected boolean mEnableCalibration = true;
	protected byte[] mInquiryResponseBytes;
	protected boolean mStreaming =false;											// This is used to monitor whether the device is in streaming mode
	//all raw params should start with a 1 byte identifier in position [0]
	protected byte[] mAccelCalRawParams = new byte[22];
	protected byte[] mDigiAccelCalRawParams  = new byte[22];
	protected byte[] mGyroCalRawParams  = new byte[22];
	protected byte[] mMagCalRawParams  = new byte[22];
	protected byte[] mPressureRawParams  = new byte[23];
	protected byte[] mEMGCalRawParams  = new byte[13];
	protected byte[] mECGCalRawParams = new byte[13];
	protected byte[] mPressureCalRawParams = new byte[23];

	//EXG
	protected byte[] mEXG1Register = new byte[10];
	protected byte[] mEXG2Register = new byte[10];
	protected int mEXG1RateSetting; //setting not value
	protected int mEXG1CH1GainSetting; // this is the setting not to be confused with the actual value
	protected int mEXG1CH1GainValue; // this is the value
	protected int mEXG1CH2GainSetting; // this is the setting not to be confused with the actual value
	protected int mEXG1CH2GainValue; // this is the value
	protected int mEXG2RateSetting; //setting not value
	protected int mEXG2CH1GainSetting; // this is the setting not to be confused with the actual value
	protected int mEXG2CH1GainValue; // this is the value
	protected int mEXG2CH2GainSetting; // this is the setting not to be confused with the actual value
	protected int mEXG2CH2GainValue; // this is the value
	protected static final int EXG_CHIP1 = 0;
	protected static final int EXG_CHIP2 = 1;
	//EXG ADVANCED
	protected int mRefenceElectrode=-1;
	protected int mLeadOffDetectionMode;
	protected int mLeadOffCurrentModeChip1;
	protected int mLeadOffCurrentModeChip2;
	protected int mComparatorsChip1;
	protected int mComparatorsChip2;
	protected int mRLDSense;
	protected int m2P1N1P;
	protected int m2P;
	protected int mLeadOffDetectionCurrent;
	protected int mLeadOffComparatorTreshold;	
	
	protected int mBaudRate=-1;
	protected byte[] mExpBoardArray; // Array where the expansion board response is stored
	protected String mExpBoardName; // Name of the expansion board. ONLY SHIMMER 3
	
	//This features are only used in LogAndStream FW 
	protected String mDirectoryName;
	protected int mDirectoryNameLenght;
	protected boolean mSensingStatus;
	protected boolean mDockedStatus;
	private List<String[]> mExtraSignalProperties = null;

	
	protected Object buildMsg(byte[] newPacket, Object object) {
		ObjectCluster objectCluster = (ObjectCluster) object;
		objectCluster.mRawData = newPacket;
		objectCluster.mSystemTimeStamp=ByteBuffer.allocate(8).putLong(System.currentTimeMillis()).array();
		double [] calibratedData=new double[mNChannels + 1]; //plus 1 because of the time stamp
		long[] newPacketInt=parsedData(newPacket,mSignalDataTypeArray);
		double[] tempData=new double[3];
		Vector3d accelerometer = new Vector3d();
		Vector3d magnetometer = new Vector3d();
		Vector3d gyroscope = new Vector3d();

		int iTimeStamp=getSignalIndex("TimeStamp"); //find index
		tempData[0]=(double)newPacketInt[1];
		objectCluster.mPropertyCluster.put("Timestamp",new FormatCluster("RAW","no units",(double)newPacketInt[iTimeStamp]));
		if (mEnableCalibration){
			objectCluster.mPropertyCluster.put("Timestamp",new FormatCluster("CAL","mSecs",calibrateTimeStamp((double)newPacketInt[iTimeStamp])));
		}
		objectCluster = callAdditionalServices(objectCluster);


		if (mShimmerVersion==SHIMMER_SR30 || mShimmerVersion==SHIMMER_3){
			if (((mEnabledSensors & SENSOR_ACCEL) > 0)){
				int iAccelX=getSignalIndex("Low Noise Accelerometer X"); //find index
				int iAccelY=getSignalIndex("Low Noise Accelerometer Y"); //find index
				int iAccelZ=getSignalIndex("Low Noise Accelerometer Z"); //find index
				tempData[0]=(double)newPacketInt[iAccelX];
				tempData[1]=(double)newPacketInt[iAccelY];
				tempData[2]=(double)newPacketInt[iAccelZ];

				objectCluster.mPropertyCluster.put("Low Noise Accelerometer X",new FormatCluster("RAW","no units",(double)newPacketInt[iAccelX]));
				objectCluster.mPropertyCluster.put("Low Noise Accelerometer Y",new FormatCluster("RAW","no units",(double)newPacketInt[iAccelY]));
				objectCluster.mPropertyCluster.put("Low Noise Accelerometer Z",new FormatCluster("RAW","no units",(double)newPacketInt[iAccelZ]));

				if (mEnableCalibration){
					double[] accelCalibratedData;
					accelCalibratedData=calibrateInertialSensorData(tempData, AlignmentMatrixAccel, SensitivityMatrixAccel, OffsetVectorAccel);
					calibratedData[iAccelX]=accelCalibratedData[0];
					calibratedData[iAccelY]=accelCalibratedData[1];
					calibratedData[iAccelZ]=accelCalibratedData[2];
					if (mDefaultCalibrationParametersAccel == true) {
						objectCluster.mPropertyCluster.put("Low Noise Accelerometer X",new FormatCluster("CAL","m/(sec^2)*",accelCalibratedData[0]));
						objectCluster.mPropertyCluster.put("Low Noise Accelerometer Y",new FormatCluster("CAL","m/(sec^2)*",accelCalibratedData[1]));
						objectCluster.mPropertyCluster.put("Low Noise Accelerometer Z",new FormatCluster("CAL","m/(sec^2)*",accelCalibratedData[2]));
						accelerometer.x=accelCalibratedData[0];
						accelerometer.y=accelCalibratedData[1];
						accelerometer.z=accelCalibratedData[2];

					} else {
						objectCluster.mPropertyCluster.put("Low Noise Accelerometer X",new FormatCluster("CAL","m/(sec^2)",accelCalibratedData[0]));
						objectCluster.mPropertyCluster.put("Low Noise Accelerometer Y",new FormatCluster("CAL","m/(sec^2)",accelCalibratedData[1]));
						objectCluster.mPropertyCluster.put("Low Noise Accelerometer Z",new FormatCluster("CAL","m/(sec^2)",accelCalibratedData[2]));
						accelerometer.x=accelCalibratedData[0];
						accelerometer.y=accelCalibratedData[1];
						accelerometer.z=accelCalibratedData[2];

					}
				}
			}
			if (((mEnabledSensors & SENSOR_DACCEL) > 0)){
				int iAccelX=getSignalIndex("Wide Range Accelerometer X"); //find index
				int iAccelY=getSignalIndex("Wide Range Accelerometer Y"); //find index
				int iAccelZ=getSignalIndex("Wide Range Accelerometer Z"); //find index
				//check range

				tempData[0]=(double)newPacketInt[iAccelX];
				tempData[1]=(double)newPacketInt[iAccelY];
				tempData[2]=(double)newPacketInt[iAccelZ];
				objectCluster.mPropertyCluster.put("Wide Range Accelerometer X",new FormatCluster("RAW","no units",(double)newPacketInt[iAccelX]));
				objectCluster.mPropertyCluster.put("Wide Range Accelerometer Y",new FormatCluster("RAW","no units",(double)newPacketInt[iAccelY]));
				objectCluster.mPropertyCluster.put("Wide Range Accelerometer Z",new FormatCluster("RAW","no units",(double)newPacketInt[iAccelZ]));


				if (mEnableCalibration){
					double[] accelCalibratedData;
					accelCalibratedData=calibrateInertialSensorData(tempData, AlignmentMatrixAccel2, SensitivityMatrixAccel2, OffsetVectorAccel2);
					calibratedData[iAccelX]=accelCalibratedData[0];
					calibratedData[iAccelY]=accelCalibratedData[1];
					calibratedData[iAccelZ]=accelCalibratedData[2];
					if (mDefaultCalibrationParametersDigitalAccel == true) {
						objectCluster.mPropertyCluster.put("Wide Range Accelerometer X",new FormatCluster("CAL","m/(sec^2)*",calibratedData[iAccelX]));
						objectCluster.mPropertyCluster.put("Wide Range Accelerometer Y",new FormatCluster("CAL","m/(sec^2)*",calibratedData[iAccelY]));
						objectCluster.mPropertyCluster.put("Wide Range Accelerometer Z",new FormatCluster("CAL","m/(sec^2)*",calibratedData[iAccelZ]));
						if (((mEnabledSensors & SENSOR_ACCEL) == 0)){
							accelerometer.x=accelCalibratedData[0]; //this is used to calculate quaternions // skip if Low noise is enabled
							accelerometer.y=accelCalibratedData[1];
							accelerometer.z=accelCalibratedData[2];
						}
					} else {
						objectCluster.mPropertyCluster.put("Wide Range Accelerometer X",new FormatCluster("CAL","m/(sec^2)",calibratedData[iAccelX]));
						objectCluster.mPropertyCluster.put("Wide Range Accelerometer Y",new FormatCluster("CAL","m/(sec^2)",calibratedData[iAccelY]));
						objectCluster.mPropertyCluster.put("Wide Range Accelerometer Z",new FormatCluster("CAL","m/(sec^2)",calibratedData[iAccelZ]));
						if (((mEnabledSensors & SENSOR_ACCEL) == 0)){
							accelerometer.x=accelCalibratedData[0];
							accelerometer.y=accelCalibratedData[1];
							accelerometer.z=accelCalibratedData[2];
						}

					}	
				}
			}
			if (((mEnabledSensors & 0xFF)& SENSOR_GYRO) > 0) {
				int iGyroX=getSignalIndex("Gyroscope X");
				int iGyroY=getSignalIndex("Gyroscope Y");
				int iGyroZ=getSignalIndex("Gyroscope Z");
				tempData[0]=(double)newPacketInt[iGyroX];
				tempData[1]=(double)newPacketInt[iGyroY];
				tempData[2]=(double)newPacketInt[iGyroZ];


				objectCluster.mPropertyCluster.put("Gyroscope X",new FormatCluster("RAW","no units",(double)newPacketInt[iGyroX]));
				objectCluster.mPropertyCluster.put("Gyroscope Y",new FormatCluster("RAW","no units",(double)newPacketInt[iGyroY]));
				objectCluster.mPropertyCluster.put("Gyroscope Z",new FormatCluster("RAW","no units",(double)newPacketInt[iGyroZ]));

				if (mEnableCalibration){
					double[] gyroCalibratedData=calibrateInertialSensorData(tempData, AlignmentMatrixGyro, SensitivityMatrixGyro, OffsetVectorGyro);
					calibratedData[iGyroX]=gyroCalibratedData[0];
					calibratedData[iGyroY]=gyroCalibratedData[1];
					calibratedData[iGyroZ]=gyroCalibratedData[2];
					if (mDefaultCalibrationParametersGyro == true) {
						objectCluster.mPropertyCluster.put("Gyroscope X",new FormatCluster("CAL","deg/sec*",gyroCalibratedData[0]));
						objectCluster.mPropertyCluster.put("Gyroscope Y",new FormatCluster("CAL","deg/sec*",gyroCalibratedData[1]));
						objectCluster.mPropertyCluster.put("Gyroscope Z",new FormatCluster("CAL","deg/sec*",gyroCalibratedData[2]));
						gyroscope.x=gyroCalibratedData[0]*Math.PI/180;
						gyroscope.y=gyroCalibratedData[1]*Math.PI/180;
						gyroscope.z=gyroCalibratedData[2]*Math.PI/180;
					} else {
						objectCluster.mPropertyCluster.put("Gyroscope X",new FormatCluster("CAL","deg/sec",gyroCalibratedData[0]));
						objectCluster.mPropertyCluster.put("Gyroscope Y",new FormatCluster("CAL","deg/sec",gyroCalibratedData[1]));
						objectCluster.mPropertyCluster.put("Gyroscope Z",new FormatCluster("CAL","deg/sec",gyroCalibratedData[2]));
						gyroscope.x=gyroCalibratedData[0]*Math.PI/180;
						gyroscope.y=gyroCalibratedData[1]*Math.PI/180;
						gyroscope.z=gyroCalibratedData[2]*Math.PI/180;
						if (mEnableOntheFlyGyroOVCal){
							mGyroX.addValue(gyroCalibratedData[0]);
							mGyroY.addValue(gyroCalibratedData[1]);
							mGyroZ.addValue(gyroCalibratedData[2]);
							mGyroXRaw.addValue((double)newPacketInt[iGyroX]);
							mGyroYRaw.addValue((double)newPacketInt[iGyroY]);
							mGyroZRaw.addValue((double)newPacketInt[iGyroZ]);
							if (mGyroX.getStandardDeviation()<mGyroOVCalThreshold && mGyroY.getStandardDeviation()<mGyroOVCalThreshold && mGyroZ.getStandardDeviation()<mGyroOVCalThreshold){
								OffsetVectorGyro[0][0]=mGyroXRaw.getMean();
								OffsetVectorGyro[1][0]=mGyroYRaw.getMean();
								OffsetVectorGyro[2][0]=mGyroZRaw.getMean();
							}
						}
					} 
				}

			}
			if (((mEnabledSensors & 0xFF)& SENSOR_MAG) > 0) {
				int iMagX=getSignalIndex("Magnetometer X");
				int iMagY=getSignalIndex("Magnetometer Y");
				int iMagZ=getSignalIndex("Magnetometer Z");
				tempData[0]=(double)newPacketInt[iMagX];
				tempData[1]=(double)newPacketInt[iMagY];
				tempData[2]=(double)newPacketInt[iMagZ];
				objectCluster.mPropertyCluster.put("Magnetometer X",new FormatCluster("RAW","no units",(double)newPacketInt[iMagX]));
				objectCluster.mPropertyCluster.put("Magnetometer Y",new FormatCluster("RAW","no units",(double)newPacketInt[iMagY]));
				objectCluster.mPropertyCluster.put("Magnetometer Z",new FormatCluster("RAW","no units",(double)newPacketInt[iMagZ]));
				if (mEnableCalibration){
					double[] magCalibratedData=calibrateInertialSensorData(tempData, AlignmentMatrixMag, SensitivityMatrixMag, OffsetVectorMag);
					calibratedData[iMagX]=magCalibratedData[0];
					calibratedData[iMagY]=magCalibratedData[1];
					calibratedData[iMagZ]=magCalibratedData[2];
					if (mDefaultCalibrationParametersMag == true) {
						objectCluster.mPropertyCluster.put("Magnetometer X",new FormatCluster("CAL","local*",magCalibratedData[0]));
						objectCluster.mPropertyCluster.put("Magnetometer Y",new FormatCluster("CAL","local*",magCalibratedData[1]));
						objectCluster.mPropertyCluster.put("Magnetometer Z",new FormatCluster("CAL","local*",magCalibratedData[2]));
						magnetometer.x=magCalibratedData[0];
						magnetometer.y=magCalibratedData[1];
						magnetometer.z=magCalibratedData[2];
					} else {
						objectCluster.mPropertyCluster.put("Magnetometer X",new FormatCluster("CAL","local",magCalibratedData[0]));
						objectCluster.mPropertyCluster.put("Magnetometer Y",new FormatCluster("CAL","local",magCalibratedData[1]));
						objectCluster.mPropertyCluster.put("Magnetometer Z",new FormatCluster("CAL","local",magCalibratedData[2]));
						magnetometer.x=magCalibratedData[0];
						magnetometer.y=magCalibratedData[1];
						magnetometer.z=magCalibratedData[2];
					}
				}
			}

			if ((mEnabledSensors & SENSOR_BATT) > 0) {
				int iA0 = getSignalIndex("VSenseBatt");
				tempData[0] = (double)newPacketInt[iA0];
				objectCluster.mPropertyCluster.put("VSenseBatt",new FormatCluster("RAW","no units",(double)newPacketInt[iA0]));
				if (mEnableCalibration){
					calibratedData[iA0]=calibrateU12AdcValue(tempData[0],0,3,1)*1.988;
					objectCluster.mPropertyCluster.put("VSenseBatt",new FormatCluster("CAL","mVolts",calibratedData[iA0]));
					mVSenseBattMA.addValue(calibratedData[iA0]);
					checkBattery();
				}
			}
			if ((mEnabledSensors & SENSOR_EXT_ADC_A7) > 0) {
				int iA0 = getSignalIndex("External ADC A7");
				tempData[0] = (double)newPacketInt[iA0];
				objectCluster.mPropertyCluster.put("External ADC A7",new FormatCluster("RAW","no units",(double)newPacketInt[iA0]));
				if (mEnableCalibration){
					calibratedData[iA0]=calibrateU12AdcValue(tempData[0],0,3,1);
					objectCluster.mPropertyCluster.put("External ADC A7",new FormatCluster("CAL","mVolts",calibratedData[iA0]));
				}
			}
			if ((mEnabledSensors & SENSOR_EXT_ADC_A6) > 0) {
				int iA0 = getSignalIndex("External ADC A6");
				tempData[0] = (double)newPacketInt[iA0];
				objectCluster.mPropertyCluster.put("External ADC A6",new FormatCluster("RAW","no units",(double)newPacketInt[iA0]));
				if (mEnableCalibration){
					calibratedData[iA0]=calibrateU12AdcValue(tempData[0],0,3,1);
					objectCluster.mPropertyCluster.put("External ADC A6",new FormatCluster("CAL","mVolts",calibratedData[iA0]));
				}
			}
			if ((mEnabledSensors & SENSOR_EXT_ADC_A15) > 0) {
				int iA0 = getSignalIndex("External ADC A15");
				tempData[0] = (double)newPacketInt[iA0];
				objectCluster.mPropertyCluster.put("External ADC A15",new FormatCluster("RAW","no units",(double)newPacketInt[iA0]));
				if (mEnableCalibration){
					calibratedData[iA0]=calibrateU12AdcValue(tempData[0],0,3,1);
					objectCluster.mPropertyCluster.put("External ADC A15",new FormatCluster("CAL","mVolts",calibratedData[iA0]));
				}
			}
			if ((mEnabledSensors & SENSOR_INT_ADC_A1) > 0) {
				int iA0 = getSignalIndex("Internal ADC A1");
				tempData[0] = (double)newPacketInt[iA0];
				objectCluster.mPropertyCluster.put("Internal ADC A1",new FormatCluster("RAW","no units",(double)newPacketInt[iA0]));
				if (mEnableCalibration){
					calibratedData[iA0]=calibrateU12AdcValue(tempData[0],0,3,1);
					objectCluster.mPropertyCluster.put("Internal ADC A1",new FormatCluster("CAL","mVolts",calibratedData[iA0]));
				}
			}
			if ((mEnabledSensors & SENSOR_INT_ADC_A12) > 0) {
				int iA0 = getSignalIndex("Internal ADC A12");
				tempData[0] = (double)newPacketInt[iA0];
				objectCluster.mPropertyCluster.put("Internal ADC A12",new FormatCluster("RAW","no units",(double)newPacketInt[iA0]));
				if (mEnableCalibration){
					calibratedData[iA0]=calibrateU12AdcValue(tempData[0],0,3,1);
					objectCluster.mPropertyCluster.put("Internal ADC A12",new FormatCluster("CAL","mVolts",calibratedData[iA0]));
				}
			}
			if ((mEnabledSensors & SENSOR_INT_ADC_A13) > 0) {
				int iA0 = getSignalIndex("Internal ADC A13");
				tempData[0] = (double)newPacketInt[iA0];
				objectCluster.mPropertyCluster.put("Internal ADC A13",new FormatCluster("RAW","no units",(double)newPacketInt[iA0]));
				if (mEnableCalibration){
					calibratedData[iA0]=calibrateU12AdcValue(tempData[0],0,3,1);
					objectCluster.mPropertyCluster.put("Internal ADC A13",new FormatCluster("CAL","mVolts",calibratedData[iA0]));
				}
			}
			if ((mEnabledSensors & SENSOR_INT_ADC_A14) > 0) {
				int iA0 = getSignalIndex("Internal ADC A14");
				tempData[0] = (double)newPacketInt[iA0];
				objectCluster.mPropertyCluster.put("Internal ADC A14",new FormatCluster("RAW","no units",(double)newPacketInt[iA0]));
				if (mEnableCalibration){
					calibratedData[iA0]=calibrateU12AdcValue(tempData[0],0,3,1);
					objectCluster.mPropertyCluster.put("Internal ADC A14",new FormatCluster("CAL","mVolts",calibratedData[iA0]));
				}
			}
			if (((mEnabledSensors & SENSOR_ACCEL) > 0 || (mEnabledSensors & SENSOR_DACCEL) > 0) && ((mEnabledSensors & 0xFF)& SENSOR_GYRO) > 0 && ((mEnabledSensors & 0xFF)& SENSOR_MAG) > 0 && mOrientationEnabled ){
				if (mEnableCalibration){
					if (mOrientationAlgo==null){
						mOrientationAlgo = new GradDes3DOrientation(0.4, (double)1/mSamplingRate, 1, 0, 0,0);
					}
					Quaternion q = mOrientationAlgo.update(accelerometer.x,accelerometer.y,accelerometer.z, gyroscope.x,gyroscope.y,gyroscope.z, magnetometer.x,magnetometer.y,magnetometer.z);					double theta, Rx, Ry, Rz, rho;
					rho = Math.acos(q.q1);
					theta = rho * 2;
					Rx = q.q2 / Math.sin(rho);
					Ry = q.q3 / Math.sin(rho);
					Rz = q.q4 / Math.sin(rho);
					objectCluster.mPropertyCluster.put("Axis Angle A",new FormatCluster("CAL","local",theta));
					objectCluster.mPropertyCluster.put("Axis Angle X",new FormatCluster("CAL","local",Rx));
					objectCluster.mPropertyCluster.put("Axis Angle Y",new FormatCluster("CAL","local",Ry));
					objectCluster.mPropertyCluster.put("Axis Angle Z",new FormatCluster("CAL","local",Rz));
					objectCluster.mPropertyCluster.put("Quaternion 0",new FormatCluster("CAL","local",q.q1));
					objectCluster.mPropertyCluster.put("Quaternion 1",new FormatCluster("CAL","local",q.q2));
					objectCluster.mPropertyCluster.put("Quaternion 2",new FormatCluster("CAL","local",q.q3));
					objectCluster.mPropertyCluster.put("Quaternion 3",new FormatCluster("CAL","local",q.q4));
				}
			}

			if ((mEnabledSensors & SENSOR_EXG1_24BIT) >0){
				int iexg1ch1 = getSignalIndex("EXG1 24Bit CH1");
				int iexg1ch2 = getSignalIndex("EXG1 24Bit CH2");
				int iexg1sta = getSignalIndex("EXG1 STATUS");
				double exg1ch1 = (double)newPacketInt[iexg1ch1];
				double exg1ch2 = (double)newPacketInt[iexg1ch2];
				double exg1sta = (double)newPacketInt[iexg1sta];
				objectCluster.mPropertyCluster.put("EXG1 STATUS",new FormatCluster("RAW","no units",exg1sta));
				if (mEnableCalibration){
					double calexg1ch1 = exg1ch1 *(((2.42*1000)/mEXG1CH1GainValue)/(Math.pow(2,23)-1));
					double calexg1ch2 = exg1ch2 *(((2.42*1000)/mEXG1CH2GainValue)/(Math.pow(2,23)-1));
					if (isEXGUsingDefaultECGConfiguration()){
						objectCluster.mPropertyCluster.put("ECG LL-RA",new FormatCluster("RAW","no units",exg1ch1));
						objectCluster.mPropertyCluster.put("ECG LA-RA",new FormatCluster("RAW","no units",exg1ch2));
						objectCluster.mPropertyCluster.put("ECG LL-RA",new FormatCluster("CAL","mVolts",calexg1ch1));
						objectCluster.mPropertyCluster.put("ECG LA-RA",new FormatCluster("CAL","mVolts",calexg1ch2));
					} else if (isEXGUsingDefaultEMGConfiguration()){
						objectCluster.mPropertyCluster.put("EMG CH1",new FormatCluster("RAW","no units",exg1ch1));
						objectCluster.mPropertyCluster.put("EMG CH2",new FormatCluster("RAW","no units",exg1ch2));
						objectCluster.mPropertyCluster.put("EMG CH1",new FormatCluster("CAL","mVolts",calexg1ch1));
						objectCluster.mPropertyCluster.put("EMG CH2",new FormatCluster("CAL","mVolts",calexg1ch2));
					} else {
						objectCluster.mPropertyCluster.put("EXG1 CH1",new FormatCluster("RAW","no units",exg1ch1));
						objectCluster.mPropertyCluster.put("EXG1 CH2",new FormatCluster("RAW","no units",exg1ch2));
						objectCluster.mPropertyCluster.put("EXG1 CH1",new FormatCluster("CAL","mVolts",calexg1ch1));
						objectCluster.mPropertyCluster.put("EXG1 CH2",new FormatCluster("CAL","mVolts",calexg1ch2));
					}
				}
			}
			if ((mEnabledSensors & SENSOR_EXG2_24BIT) >0){
				int iexg2ch1 = getSignalIndex("EXG2 24Bit CH1");
				int iexg2ch2 = getSignalIndex("EXG2 24Bit CH2");
				int iexg2sta = getSignalIndex("EXG2 STATUS");
				double exg2ch1 = (double)newPacketInt[iexg2ch1];
				double exg2ch2 = (double)newPacketInt[iexg2ch2];
				double exg2sta = (double)newPacketInt[iexg2sta];

				objectCluster.mPropertyCluster.put("EXG2 STATUS",new FormatCluster("RAW","no units",exg2sta));
				if (mEnableCalibration){
					double calexg2ch1 = exg2ch1 *(((2.42*1000)/mEXG2CH1GainValue)/(Math.pow(2,23)-1));
					double calexg2ch2 = exg2ch2 *(((2.42*1000)/mEXG2CH2GainValue)/(Math.pow(2,23)-1));
					if (isEXGUsingDefaultECGConfiguration()){
						objectCluster.mPropertyCluster.put("EXG2 CH1",new FormatCluster("RAW","no units",exg2ch1));
						objectCluster.mPropertyCluster.put("ECG Vx-RL",new FormatCluster("RAW","no units",exg2ch2));
						objectCluster.mPropertyCluster.put("EXG2 CH1",new FormatCluster("CAL","mVolts",calexg2ch1));
						objectCluster.mPropertyCluster.put("ECG Vx-RL",new FormatCluster("CAL","mVolts",calexg2ch2));
					}
					else if (isEXGUsingDefaultEMGConfiguration()){
						objectCluster.mPropertyCluster.put("EXG2 CH1",new FormatCluster("RAW","no units",0));
						objectCluster.mPropertyCluster.put("EXG2 CH2",new FormatCluster("RAW","no units",0));
						objectCluster.mPropertyCluster.put("EXG2 CH1",new FormatCluster("CAL","mVolts",0));
						objectCluster.mPropertyCluster.put("EXG2 CH2",new FormatCluster("CAL","mVolts",0));
					} else {
						objectCluster.mPropertyCluster.put("EXG2 CH1",new FormatCluster("RAW","no units",exg2ch1));
						objectCluster.mPropertyCluster.put("EXG2 CH2",new FormatCluster("RAW","no units",exg2ch2));
						objectCluster.mPropertyCluster.put("EXG2 CH1",new FormatCluster("CAL","mVolts",calexg2ch1));
						objectCluster.mPropertyCluster.put("EXG2 CH2",new FormatCluster("CAL","mVolts",calexg2ch2));	
					}
				}
			}

			if ((mEnabledSensors & SENSOR_EXG1_16BIT) >0){
				int iexg1ch1 = getSignalIndex("EXG1 16Bit CH1");
				int iexg1ch2 = getSignalIndex("EXG1 16Bit CH2");
				int iexg1sta = getSignalIndex("EXG1 STATUS");
				double exg1ch1 = (double)newPacketInt[iexg1ch1];
				double exg1ch2 = (double)newPacketInt[iexg1ch2];
				double exg1sta = (double)newPacketInt[iexg1sta];
				objectCluster.mPropertyCluster.put("EXG1 STATUS",new FormatCluster("RAW","no units",exg1sta));
				if (mEnableCalibration){
					double calexg1ch1 = exg1ch1 *(((2.42*1000)/(mEXG1CH1GainValue*2))/(Math.pow(2,15)-1));
					double calexg1ch2 = exg1ch2 *(((2.42*1000)/(mEXG1CH2GainValue*2))/(Math.pow(2,15)-1));
					if (isEXGUsingDefaultECGConfiguration()){
						objectCluster.mPropertyCluster.put("ECG LL-RA",new FormatCluster("RAW","no units",exg1ch1));
						objectCluster.mPropertyCluster.put("ECG LA-RA",new FormatCluster("RAW","no units",exg1ch2));
						objectCluster.mPropertyCluster.put("ECG LL-RA",new FormatCluster("CAL","mVolts",calexg1ch1));
						objectCluster.mPropertyCluster.put("ECG LA-RA",new FormatCluster("CAL","mVolts",calexg1ch2));
					} else if (isEXGUsingDefaultEMGConfiguration()){
						objectCluster.mPropertyCluster.put("EMG CH1",new FormatCluster("RAW","no units",exg1ch1));
						objectCluster.mPropertyCluster.put("EMG CH2",new FormatCluster("RAW","no units",exg1ch2));
						objectCluster.mPropertyCluster.put("EMG CH1",new FormatCluster("CAL","mVolts",calexg1ch1));
						objectCluster.mPropertyCluster.put("EMG CH2",new FormatCluster("CAL","mVolts",calexg1ch2));
					} else {
						objectCluster.mPropertyCluster.put("EXG1 CH1 16Bit",new FormatCluster("RAW","no units",exg1ch1));
						objectCluster.mPropertyCluster.put("EXG1 CH2 16Bit",new FormatCluster("RAW","no units",exg1ch2));
						objectCluster.mPropertyCluster.put("EXG1 CH1 16Bit",new FormatCluster("CAL","mVolts",calexg1ch1));
						objectCluster.mPropertyCluster.put("EXG1 CH2 16Bit",new FormatCluster("CAL","mVolts",calexg1ch2));
					}
				}
			}
			if ((mEnabledSensors & SENSOR_EXG2_16BIT) >0){
				int iexg2ch1 = getSignalIndex("EXG2 16Bit CH1");
				int iexg2ch2 = getSignalIndex("EXG2 16Bit CH2");
				int iexg2sta = getSignalIndex("EXG2 STATUS");
				double exg2ch1 = (double)newPacketInt[iexg2ch1];
				double exg2ch2 = (double)newPacketInt[iexg2ch2];
				double exg2sta = (double)newPacketInt[iexg2sta];

				objectCluster.mPropertyCluster.put("EXG2 STATUS",new FormatCluster("RAW","no units",exg2sta));
				if (mEnableCalibration){
					double calexg2ch1 = ((exg2ch1)) *(((2.42*1000)/(mEXG2CH1GainValue*2))/(Math.pow(2,15)-1));
					double calexg2ch2 = ((exg2ch2)) *(((2.42*1000)/(mEXG2CH2GainValue*2))/(Math.pow(2,15)-1));
					if (isEXGUsingDefaultECGConfiguration()){
						objectCluster.mPropertyCluster.put("EXG2 CH1",new FormatCluster("RAW","no units",exg2ch1));
						objectCluster.mPropertyCluster.put("ECG Vx-RL",new FormatCluster("RAW","no units",exg2ch2));
						objectCluster.mPropertyCluster.put("EXG2 CH1",new FormatCluster("CAL","mVolts",calexg2ch1));
						objectCluster.mPropertyCluster.put("ECG Vx-RL",new FormatCluster("CAL","mVolts",calexg2ch2));
					}
					else if (isEXGUsingDefaultEMGConfiguration()){
						objectCluster.mPropertyCluster.put("EXG2 CH1",new FormatCluster("RAW","no units",0));
						objectCluster.mPropertyCluster.put("EXG2 CH2",new FormatCluster("RAW","no units",0));
						objectCluster.mPropertyCluster.put("EXG2 CH1",new FormatCluster("CAL","mVolts",0));
						objectCluster.mPropertyCluster.put("EXG2 CH2",new FormatCluster("CAL","mVolts",0));
					} else {
						objectCluster.mPropertyCluster.put("EXG2 CH1 16Bit",new FormatCluster("RAW","no units",exg2ch1));
						objectCluster.mPropertyCluster.put("EXG2 CH2 16Bit",new FormatCluster("RAW","no units",exg2ch2));
						objectCluster.mPropertyCluster.put("EXG2 CH1 16Bit",new FormatCluster("CAL","mVolts",calexg2ch1));
						objectCluster.mPropertyCluster.put("EXG2 CH2 16Bit",new FormatCluster("CAL","mVolts",calexg2ch2));
					}
				}
			}

			if ((mEnabledSensors & SENSOR_BMP180) >0){
				int iUT = getSignalIndex("Temperature");
				int iUP = getSignalIndex("Pressure");
				double UT = (double)newPacketInt[iUT];
				double UP = (double)newPacketInt[iUP];
				UP=UP/Math.pow(2,8-mPressureResolution);
				objectCluster.mPropertyCluster.put("Pressure",new FormatCluster("RAW","no units",UP));
				objectCluster.mPropertyCluster.put("Temperature",new FormatCluster("RAW","no units",UT));
				if (mEnableCalibration){
					double[] bmp180caldata= calibratePressureSensorData(UP,UT);
					objectCluster.mPropertyCluster.put("Pressure",new FormatCluster("CAL","kPa",bmp180caldata[0]/1000));
					objectCluster.mPropertyCluster.put("Temperature",new FormatCluster("CAL","Celsius",bmp180caldata[1]));
				}
			}

			if ((mEnabledSensors & SENSOR_BRIDGE_AMP) > 0) {
				int iBAHigh = getSignalIndex("Bridge Amplifier High");
				int iBALow = getSignalIndex("Bridge Amplifier Low");
				objectCluster.mPropertyCluster.put("Bridge Amplifier High",new FormatCluster("RAW","no units",(double)newPacketInt[iBAHigh]));
				objectCluster.mPropertyCluster.put("Bridge Amplifier Low",new FormatCluster("RAW","no units",(double)newPacketInt[iBALow]));
				if (mEnableCalibration){
					tempData[0] = (double)newPacketInt[iBAHigh];
					tempData[1] = (double)newPacketInt[iBALow];
					calibratedData[iBAHigh]=calibrateU12AdcValue(tempData[0],60,3,551);
					calibratedData[iBALow]=calibrateU12AdcValue(tempData[1],1950,3,183.7);
					objectCluster.mPropertyCluster.put("Bridge Amplifier High",new FormatCluster("CAL","mVolts",calibratedData[iBAHigh]));
					objectCluster.mPropertyCluster.put("Bridge Amplifier Low",new FormatCluster("CAL","mVolts",calibratedData[iBALow]));	
				}
			}

			if ((mEnabledSensors & SENSOR_GSR) > 0) {
				int iGSR = getSignalIndex("GSR Raw");
				tempData[0] = (double)newPacketInt[iGSR];
				int newGSRRange = -1; // initialized to -1 so it will only come into play if mGSRRange = 4  

				double p1=0,p2=0;//,p3=0,p4=0,p5=0;
				if (mGSRRange==4){
					newGSRRange=(49152 & (int)tempData[0])>>14; 
				}
				if (mGSRRange==0 || newGSRRange==0) { //Note that from FW 1.0 onwards the MSB of the GSR data contains the range
					// the polynomial function used for calibration has been deprecated, it is replaced with a linear function
					/* p1 = 6.5995E-9;
		                    p2 = -6.895E-5;
		                    p3 = 2.699E-1;
		                    p4 = -4.769835E+2;
		                    p5 = 3.403513341E+5;*/
					if (mShimmerVersion!=SHIMMER_3){
						p1 = 0.0373;
						p2 = -24.9915;
					} else {
						p1 = 0.0363;
						p2 = -24.8617;
					}
				} else if (mGSRRange==1 || newGSRRange==1) {
					/*p1 = 1.3569627E-8;
		                    p2 = -1.650399E-4;
		                    p3 = 7.54199E-1;
		                    p4 = -1.5726287856E+3;
		                    p5 = 1.367507927E+6;*/
					if (mShimmerVersion!=SHIMMER_3){
						p1 = 0.0054;
						p2 = -3.5194;
					} else {
						p1 = 0.0051;
						p2 = -3.8357;
					}
				} else if (mGSRRange==2 || newGSRRange==2) {
					/*p1 = 2.550036498E-8;
		                    p2 = -3.3136E-4;
		                    p3 = 1.6509426597E+0;
		                    p4 = -3.833348044E+3;
		                    p5 = 3.8063176947E+6;*/
					if (mShimmerVersion!=SHIMMER_3){
						p1 = 0.0015;
						p2 = -1.0163;
					} else {
						p1 = 0.0015;
						p2 = -1.0067;
					}
				} else if (mGSRRange==3  || newGSRRange==3) {
					/*p1 = 3.7153627E-7;
		                    p2 = -4.239437E-3;
		                    p3 = 1.7905709E+1;
		                    p4 = -3.37238657E+4;
		                    p5 = 2.53680446279E+7;*/
					if (mShimmerVersion!=SHIMMER_3){
						p1 = 4.5580e-04;
						p2 = -0.3014;
					} else {
						p1 = 4.4513e-04;
						p2 = -0.3193;
					}
				}
				objectCluster.mPropertyCluster.put("GSR",new FormatCluster("RAW","no units",(double)newPacketInt[iGSR]));
				if (mEnableCalibration){
					calibratedData[iGSR] = calibrateGsrData(tempData[0],p1,p2);
					objectCluster.mPropertyCluster.put("GSR",new FormatCluster("CAL","kOhms",calibratedData[iGSR]));
				}
			}
		} else { //start of Shimmer2

			if ((mEnabledSensors & SENSOR_ACCEL) > 0){
				int iAccelX=getSignalIndex("Accelerometer X"); //find index
				int iAccelY=getSignalIndex("Accelerometer Y"); //find index
				int iAccelZ=getSignalIndex("Accelerometer Z"); //find index
				objectCluster.mPropertyCluster.put("Accelerometer X",new FormatCluster("RAW","no units",(double)newPacketInt[iAccelX]));
				objectCluster.mPropertyCluster.put("Accelerometer Y",new FormatCluster("RAW","no units",(double)newPacketInt[iAccelY]));
				objectCluster.mPropertyCluster.put("Accelerometer Z",new FormatCluster("RAW","no units",(double)newPacketInt[iAccelZ]));
				if (mEnableCalibration){
					tempData[0]=(double)newPacketInt[iAccelX];
					tempData[1]=(double)newPacketInt[iAccelY];
					tempData[2]=(double)newPacketInt[iAccelZ];
					double[] accelCalibratedData=calibrateInertialSensorData(tempData, AlignmentMatrixAccel, SensitivityMatrixAccel, OffsetVectorAccel);
					calibratedData[iAccelX]=accelCalibratedData[0];
					calibratedData[iAccelY]=accelCalibratedData[1];
					calibratedData[iAccelZ]=accelCalibratedData[2];
					if (mDefaultCalibrationParametersAccel == true) {
						objectCluster.mPropertyCluster.put("Accelerometer X",new FormatCluster("CAL","m/(sec^2)*",accelCalibratedData[0]));
						objectCluster.mPropertyCluster.put("Accelerometer Y",new FormatCluster("CAL","m/(sec^2)*",accelCalibratedData[1]));
						objectCluster.mPropertyCluster.put("Accelerometer Z",new FormatCluster("CAL","m/(sec^2)*",accelCalibratedData[2]));
						accelerometer.x=accelCalibratedData[0];
						accelerometer.y=accelCalibratedData[1];
						accelerometer.z=accelCalibratedData[2];
					} else {
						objectCluster.mPropertyCluster.put("Accelerometer X",new FormatCluster("CAL","m/(sec^2)",accelCalibratedData[0]));
						objectCluster.mPropertyCluster.put("Accelerometer Y",new FormatCluster("CAL","m/(sec^2)",accelCalibratedData[1]));
						objectCluster.mPropertyCluster.put("Accelerometer Z",new FormatCluster("CAL","m/(sec^2)",accelCalibratedData[2]));
						accelerometer.x=accelCalibratedData[0];
						accelerometer.y=accelCalibratedData[1];
						accelerometer.z=accelCalibratedData[2];
					}
				}

			}

			if ((mEnabledSensors & SENSOR_GYRO) > 0) {
				int iGyroX=getSignalIndex("Gyroscope X");
				int iGyroY=getSignalIndex("Gyroscope Y");
				int iGyroZ=getSignalIndex("Gyroscope Z");					
				objectCluster.mPropertyCluster.put("Gyroscope X",new FormatCluster("RAW","no units",(double)newPacketInt[iGyroX]));
				objectCluster.mPropertyCluster.put("Gyroscope Y",new FormatCluster("RAW","no units",(double)newPacketInt[iGyroY]));
				objectCluster.mPropertyCluster.put("Gyroscope Z",new FormatCluster("RAW","no units",(double)newPacketInt[iGyroZ]));
				if (mEnableCalibration){
					tempData[0]=(double)newPacketInt[iGyroX];
					tempData[1]=(double)newPacketInt[iGyroY];
					tempData[2]=(double)newPacketInt[iGyroZ];
					double[] gyroCalibratedData=calibrateInertialSensorData(tempData, AlignmentMatrixGyro, SensitivityMatrixGyro, OffsetVectorGyro);
					calibratedData[iGyroX]=gyroCalibratedData[0];
					calibratedData[iGyroY]=gyroCalibratedData[1];
					calibratedData[iGyroZ]=gyroCalibratedData[2];
					if (mDefaultCalibrationParametersGyro == true) {
						objectCluster.mPropertyCluster.put("Gyroscope X",new FormatCluster("CAL","deg/sec*",gyroCalibratedData[0]));
						objectCluster.mPropertyCluster.put("Gyroscope Y",new FormatCluster("CAL","deg/sec*",gyroCalibratedData[1]));
						objectCluster.mPropertyCluster.put("Gyroscope Z",new FormatCluster("CAL","deg/sec*",gyroCalibratedData[2]));
						gyroscope.x=gyroCalibratedData[0]*Math.PI/180;
						gyroscope.y=gyroCalibratedData[1]*Math.PI/180;
						gyroscope.z=gyroCalibratedData[2]*Math.PI/180;
					} else {
						objectCluster.mPropertyCluster.put("Gyroscope X",new FormatCluster("CAL","deg/sec",gyroCalibratedData[0]));
						objectCluster.mPropertyCluster.put("Gyroscope Y",new FormatCluster("CAL","deg/sec",gyroCalibratedData[1]));
						objectCluster.mPropertyCluster.put("Gyroscope Z",new FormatCluster("CAL","deg/sec",gyroCalibratedData[2]));
						gyroscope.x=gyroCalibratedData[0]*Math.PI/180;
						gyroscope.y=gyroCalibratedData[1]*Math.PI/180;
						gyroscope.z=gyroCalibratedData[2]*Math.PI/180;
						if (mEnableOntheFlyGyroOVCal){
							mGyroX.addValue(gyroCalibratedData[0]);
							mGyroY.addValue(gyroCalibratedData[1]);
							mGyroZ.addValue(gyroCalibratedData[2]);
							mGyroXRaw.addValue((double)newPacketInt[iGyroX]);
							mGyroYRaw.addValue((double)newPacketInt[iGyroY]);
							mGyroZRaw.addValue((double)newPacketInt[iGyroZ]);
							if (mGyroX.getStandardDeviation()<mGyroOVCalThreshold && mGyroY.getStandardDeviation()<mGyroOVCalThreshold && mGyroZ.getStandardDeviation()<mGyroOVCalThreshold){
								OffsetVectorGyro[0][0]=mGyroXRaw.getMean();
								OffsetVectorGyro[1][0]=mGyroYRaw.getMean();
								OffsetVectorGyro[2][0]=mGyroZRaw.getMean();
							}
						}
					} 
				}
			}
			if ((mEnabledSensors & SENSOR_MAG) > 0) {
				int iMagX=getSignalIndex("Magnetometer X");
				int iMagY=getSignalIndex("Magnetometer Y");
				int iMagZ=getSignalIndex("Magnetometer Z");
				objectCluster.mPropertyCluster.put("Magnetometer X",new FormatCluster("RAW","no units",(double)newPacketInt[iMagX]));
				objectCluster.mPropertyCluster.put("Magnetometer Y",new FormatCluster("RAW","no units",(double)newPacketInt[iMagY]));
				objectCluster.mPropertyCluster.put("Magnetometer Z",new FormatCluster("RAW","no units",(double)newPacketInt[iMagZ]));
				if (mEnableCalibration){
					tempData[0]=(double)newPacketInt[iMagX];
					tempData[1]=(double)newPacketInt[iMagY];
					tempData[2]=(double)newPacketInt[iMagZ];
					double[] magCalibratedData=calibrateInertialSensorData(tempData, AlignmentMatrixMag, SensitivityMatrixMag, OffsetVectorMag);
					calibratedData[iMagX]=magCalibratedData[0];
					calibratedData[iMagY]=magCalibratedData[1];
					calibratedData[iMagZ]=magCalibratedData[2];
					if (mDefaultCalibrationParametersMag == true) {
						objectCluster.mPropertyCluster.put("Magnetometer X",new FormatCluster("CAL","local*",magCalibratedData[0]));
						objectCluster.mPropertyCluster.put("Magnetometer Y",new FormatCluster("CAL","local*",magCalibratedData[1]));
						objectCluster.mPropertyCluster.put("Magnetometer Z",new FormatCluster("CAL","local*",magCalibratedData[2]));
						magnetometer.x=magCalibratedData[0];
						magnetometer.y=magCalibratedData[1];
						magnetometer.z=magCalibratedData[2];
					} else {
						objectCluster.mPropertyCluster.put("Magnetometer X",new FormatCluster("CAL","local",magCalibratedData[0]));
						objectCluster.mPropertyCluster.put("Magnetometer Y",new FormatCluster("CAL","local",magCalibratedData[1]));
						objectCluster.mPropertyCluster.put("Magnetometer Z",new FormatCluster("CAL","local",magCalibratedData[2]));
						magnetometer.x=magCalibratedData[0];
						magnetometer.y=magCalibratedData[1];
						magnetometer.z=magCalibratedData[2];
					}
				}
			}


			if ((mEnabledSensors & SENSOR_ACCEL) > 0 && (mEnabledSensors & SENSOR_GYRO) > 0 && (mEnabledSensors & SENSOR_MAG) > 0 && mOrientationEnabled ){
				if (mEnableCalibration){
					if (mOrientationAlgo==null){
						mOrientationAlgo = new GradDes3DOrientation(0.4, (double)1/mSamplingRate, 1, 0, 0,0);
					}
					Quaternion q = mOrientationAlgo.update(accelerometer.x,accelerometer.y,accelerometer.z, gyroscope.x,gyroscope.y,gyroscope.z, magnetometer.x,magnetometer.y,magnetometer.z);
					double theta, Rx, Ry, Rz, rho;
					rho = Math.acos(q.q1);
					theta = rho * 2;
					Rx = q.q2 / Math.sin(rho);
					Ry = q.q3 / Math.sin(rho);
					Rz = q.q4 / Math.sin(rho);
					objectCluster.mPropertyCluster.put("Axis Angle A",new FormatCluster("CAL","local",theta));
					objectCluster.mPropertyCluster.put("Axis Angle X",new FormatCluster("CAL","local",Rx));
					objectCluster.mPropertyCluster.put("Axis Angle Y",new FormatCluster("CAL","local",Ry));
					objectCluster.mPropertyCluster.put("Axis Angle Z",new FormatCluster("CAL","local",Rz));
					objectCluster.mPropertyCluster.put("Quaternion 0",new FormatCluster("CAL","local",q.q1));
					objectCluster.mPropertyCluster.put("Quaternion 1",new FormatCluster("CAL","local",q.q2));
					objectCluster.mPropertyCluster.put("Quaternion 2",new FormatCluster("CAL","local",q.q3));
					objectCluster.mPropertyCluster.put("Quaternion 3",new FormatCluster("CAL","local",q.q4));
				}
			}

			if ((mEnabledSensors & SENSOR_GSR) > 0) {
				int iGSR = getSignalIndex("GSR Raw");
				tempData[0] = (double)newPacketInt[iGSR];
				int newGSRRange = -1; // initialized to -1 so it will only come into play if mGSRRange = 4  

				double p1=0,p2=0;//,p3=0,p4=0,p5=0;
				if (mGSRRange==4){
					newGSRRange=(49152 & (int)tempData[0])>>14; 
				}
				if (mGSRRange==0 || newGSRRange==0) { //Note that from FW 1.0 onwards the MSB of the GSR data contains the range
					// the polynomial function used for calibration has been deprecated, it is replaced with a linear function
					/* p1 = 6.5995E-9;
		                    p2 = -6.895E-5;
		                    p3 = 2.699E-1;
		                    p4 = -4.769835E+2;
		                    p5 = 3.403513341E+5;*/
					p1 = 0.0373;
					p2 = -24.9915;
				} else if (mGSRRange==1 || newGSRRange==1) {
					/*p1 = 1.3569627E-8;
		                    p2 = -1.650399E-4;
		                    p3 = 7.54199E-1;
		                    p4 = -1.5726287856E+3;
		                    p5 = 1.367507927E+6;*/
					p1 = 0.0054;
					p2 = -3.5194;
				} else if (mGSRRange==2 || newGSRRange==2) {
					/*p1 = 2.550036498E-8;
		                    p2 = -3.3136E-4;
		                    p3 = 1.6509426597E+0;
		                    p4 = -3.833348044E+3;
		                    p5 = 3.8063176947E+6;*/
					p1 = 0.0015;
					p2 = -1.0163;
				} else if (mGSRRange==3  || newGSRRange==3) {
					/*p1 = 3.7153627E-7;
		                    p2 = -4.239437E-3;
		                    p3 = 1.7905709E+1;
		                    p4 = -3.37238657E+4;
		                    p5 = 2.53680446279E+7;*/
					p1 = 4.5580e-04;
					p2 = -0.3014;
				}
				objectCluster.mPropertyCluster.put("GSR",new FormatCluster("RAW","no units",(double)newPacketInt[iGSR]));
				if (mEnableCalibration){
					tempData[0] = (double)newPacketInt[iGSR];
					calibratedData[iGSR] = calibrateGsrData(tempData[0],p1,p2);
					objectCluster.mPropertyCluster.put("GSR",new FormatCluster("CAL","kOhms",calibratedData[iGSR]));
				}
			}
			if ((mEnabledSensors & SENSOR_ECG) > 0) {
				int iECGRALL = getSignalIndex("ECG RA LL");
				int iECGLALL = getSignalIndex("ECG LA LL");
				objectCluster.mPropertyCluster.put("ECG RA-LL",new FormatCluster("RAW","no units",(double)newPacketInt[iECGRALL]));
				objectCluster.mPropertyCluster.put("ECG LA-LL",new FormatCluster("RAW","no units",(double)newPacketInt[iECGLALL]));
				if (mEnableCalibration){
					tempData[0] = (double)newPacketInt[iECGRALL];
					tempData[1] = (double)newPacketInt[iECGLALL];
					calibratedData[iECGRALL]=calibrateU12AdcValue(tempData[0],OffsetECGRALL,3,GainECGRALL);
					calibratedData[iECGLALL]=calibrateU12AdcValue(tempData[1],OffsetECGLALL,3,GainECGLALL);
					if (mDefaultCalibrationParametersECG == true) {
						objectCluster.mPropertyCluster.put("ECG RA-LL",new FormatCluster("CAL","mVolts*",calibratedData[iECGRALL]));
						objectCluster.mPropertyCluster.put("ECG LA-LL",new FormatCluster("CAL","mVolts*",calibratedData[iECGLALL]));
					} else {
						objectCluster.mPropertyCluster.put("ECG RA-LL",new FormatCluster("CAL","mVolts",calibratedData[iECGRALL]));
						objectCluster.mPropertyCluster.put("ECG LA-LL",new FormatCluster("CAL","mVolts",calibratedData[iECGLALL]));
					}
				}
			}
			if ((mEnabledSensors & SENSOR_EMG) > 0) {
				int iEMG = getSignalIndex("EMG");
				objectCluster.mPropertyCluster.put("EMG",new FormatCluster("RAW","no units",(double)newPacketInt[iEMG]));
				if (mEnableCalibration){
					tempData[0] = (double)newPacketInt[iEMG];
					calibratedData[iEMG]=calibrateU12AdcValue(tempData[0],OffsetEMG,3,GainEMG);
					if (mDefaultCalibrationParametersEMG == true){
						objectCluster.mPropertyCluster.put("EMG",new FormatCluster("CAL","mVolts*",calibratedData[iEMG]));
					} else {
						objectCluster.mPropertyCluster.put("EMG",new FormatCluster("CAL","mVolts",calibratedData[iEMG]));
					}
				}
			}
			if ((mEnabledSensors & SENSOR_BRIDGE_AMP) > 0) {
				int iBAHigh = getSignalIndex("Bridge Amplifier High");
				int iBALow = getSignalIndex("Bridge Amplifier Low");
				objectCluster.mPropertyCluster.put("Bridge Amplifier High",new FormatCluster("RAW","no units",(double)newPacketInt[iBAHigh]));
				objectCluster.mPropertyCluster.put("Bridge Amplifier Low",new FormatCluster("RAW","no units",(double)newPacketInt[iBALow]));
				if (mEnableCalibration){
					tempData[0] = (double)newPacketInt[iBAHigh];
					tempData[1] = (double)newPacketInt[iBALow];
					calibratedData[iBAHigh]=calibrateU12AdcValue(tempData[0],60,3,551);
					calibratedData[iBALow]=calibrateU12AdcValue(tempData[1],1950,3,183.7);
					objectCluster.mPropertyCluster.put("Bridge Amplifier High",new FormatCluster("CAL","mVolts",calibratedData[iBAHigh]));
					objectCluster.mPropertyCluster.put("Bridge Amplifier Low",new FormatCluster("CAL","mVolts",calibratedData[iBALow]));	
				}
			}
			if ((mEnabledSensors & SENSOR_HEART) > 0) {
				int iHeartRate = getSignalIndex("Heart Rate");
				tempData[0] = (double)newPacketInt[iHeartRate];
				objectCluster.mPropertyCluster.put("Heart Rate",new FormatCluster("RAW","no units",tempData[0]));
				if (mEnableCalibration){
					calibratedData[iHeartRate]=tempData[0];
					if (mFWMajorVersion==0 && mFWMinorVersion==1){

					} else {
						if (tempData[0]==0){
							calibratedData[iHeartRate]=	mLastKnownHeartRate;
						} else {
							calibratedData[iHeartRate]=(int)(1024/tempData[0]*60);
							mLastKnownHeartRate=calibratedData[iHeartRate];
						}
					}
					objectCluster.mPropertyCluster.put("Heart Rate",new FormatCluster("CAL","BPM",calibratedData[iHeartRate]));	
				}
			}
			if ((mEnabledSensors& SENSOR_EXP_BOARD_A0) > 0) {
				int iA0 = getSignalIndex("Exp Board A0");
				tempData[0] = (double)newPacketInt[iA0];
				if (getPMux()==0){
					objectCluster.mPropertyCluster.put("ExpBoard A0",new FormatCluster("RAW","no units",(double)newPacketInt[iA0]));
					if (mEnableCalibration){
						calibratedData[iA0]=calibrateU12AdcValue(tempData[0],0,3,1);
						objectCluster.mPropertyCluster.put("ExpBoard A0",new FormatCluster("CAL","mVolts",calibratedData[iA0]));
					}
				} else {
					objectCluster.mPropertyCluster.put("VSenseReg",new FormatCluster("RAW","no units",(double)newPacketInt[iA0]));
					if (mEnableCalibration){
						calibratedData[iA0]=calibrateU12AdcValue(tempData[0],0,3,1)*1.988;
						objectCluster.mPropertyCluster.put("VSenseReg",new FormatCluster("CAL","mVolts",calibratedData[iA0]));
					}

				}
			}					
			if ((mEnabledSensors & SENSOR_EXP_BOARD_A7) > 0) {
				int iA7 = getSignalIndex("Exp Board A7");
				tempData[0] = (double)newPacketInt[iA7];
				if (getPMux()==0){
					objectCluster.mPropertyCluster.put("ExpBoard A7",new FormatCluster("RAW","no units",(double)newPacketInt[iA7]));
					if (mEnableCalibration){
						calibratedData[iA7]=calibrateU12AdcValue(tempData[0],0,3,1);
						objectCluster.mPropertyCluster.put("ExpBoard A7",new FormatCluster("CAL","mVolts",calibratedData[iA7]));
					}
				} 
			}
			if  ((mEnabledSensors & SENSOR_BATT) > 0) {
				int iA0 = getSignalIndex("Exp Board A0");
				objectCluster.mPropertyCluster.put("VSenseReg",new FormatCluster("RAW","no units",(double)newPacketInt[iA0]));

				int iA7 = getSignalIndex("Exp Board A7");
				objectCluster.mPropertyCluster.put("VSenseBatt",new FormatCluster("RAW","no units",(double)newPacketInt[iA7]));


				if (mEnableCalibration){
					tempData[0] = (double)newPacketInt[iA0];
					calibratedData[iA0]=calibrateU12AdcValue(tempData[0],0,3,1)*1.988;
					objectCluster.mPropertyCluster.put("VSenseReg",new FormatCluster("CAL","mVolts",calibratedData[iA0]));

					tempData[0] = (double)newPacketInt[iA7];
					calibratedData[iA7]=calibrateU12AdcValue(tempData[0],0,3,1)*2;
					objectCluster.mPropertyCluster.put("VSenseBatt",new FormatCluster("CAL","mVolts",calibratedData[iA7]));	
					mVSenseBattMA.addValue(calibratedData[iA7]);
					checkBattery();
				}
			}
		}
		
		
		
		return objectCluster;
	}




	//protected abstract void writeLEDCommand(int i);

	/**
	 * Converts the raw packet byte values, into the corresponding calibrated and uncalibrated sensor values, the Instruction String determines the output 
	 * @param newPacket a byte array containing the current received packet
	 * @param Instructions an array string containing the commands to execute. It is currently not fully supported
	 * @return
	 */

	protected long[] parsedData(byte[] data,String[] dataType)
	{
		int iData=0;
		long[] formattedData=new long[dataType.length];

		for (int i=0;i<dataType.length;i++)
			if (dataType[i]=="u8") {
				formattedData[i]=(int)data[iData];
				iData=iData+1;
			} else if (dataType[i]=="i8") {
				formattedData[i]=calculatetwoscomplement((int)((int)0xFF & data[iData]),8);
				iData=iData+1;
			} else if (dataType[i]=="u12") {

				formattedData[i]=(int)((int)(data[iData] & 0xFF) + ((int)(data[iData+1] & 0xFF) << 8));
				iData=iData+2;
			} else if (dataType[i]=="i12>") {
				formattedData[i]=calculatetwoscomplement((int)((int)(data[iData] & 0xFF) + ((int)(data[iData+1] & 0xFF) << 8)),16);
				formattedData[i]=formattedData[i]>>4; // shift right by 4 bits
				iData=iData+2;
			} else if (dataType[i]=="u16") {				
				formattedData[i]=(int)((int)(data[iData] & 0xFF) + ((int)(data[iData+1] & 0xFF) << 8));
				iData=iData+2;
			} else if (dataType[i]=="u16r") {				
				formattedData[i]=(int)((int)(data[iData+1] & 0xFF) + ((int)(data[iData+0] & 0xFF) << 8));
				iData=iData+2;
			} else if (dataType[i]=="i16") {
				formattedData[i]=calculatetwoscomplement((int)((int)(data[iData] & 0xFF) + ((int)(data[iData+1] & 0xFF) << 8)),16);
				//formattedData[i]=ByteBuffer.wrap(arrayb).order(ByteOrder.LITTLE_ENDIAN).getShort();
				iData=iData+2;
			} else if (dataType[i]=="i16r"){
				formattedData[i]=calculatetwoscomplement((int)((int)(data[iData+1] & 0xFF) + ((int)(data[iData] & 0xFF) << 8)),16);
				//formattedData[i]=ByteBuffer.wrap(arrayb).order(ByteOrder.LITTLE_ENDIAN).getShort();
				iData=iData+2;
			} else if (dataType[i]=="u24r") {
				long xmsb =((long)(data[iData+0] & 0xFF) << 16);
				long msb =((long)(data[iData+1] & 0xFF) << 8);
				long lsb =((long)(data[iData+2] & 0xFF));
				formattedData[i]=xmsb + msb + lsb;
				iData=iData+3;
			} else if (dataType[i]=="i24r") {
				long xmsb =((long)(data[iData+0] & 0xFF) << 16);
				long msb =((long)(data[iData+1] & 0xFF) << 8);
				long lsb =((long)(data[iData+2] & 0xFF));
				formattedData[i]=calculatetwoscomplement((int)(xmsb + msb + lsb),24);
				iData=iData+3;
			} 
		return formattedData;
	}
	/*
	 * Data Methods
	 * */  


	private int[] formatdatapacketreverse(byte[] data,String[] dataType)
	{
		int iData=0;
		int[] formattedData=new int[dataType.length];

		for (int i=0;i<dataType.length;i++)
			if (dataType[i]=="u8") {
				formattedData[i]=(int)data[iData];
				iData=iData+1;
			}
			else if (dataType[i]=="i8") {
				formattedData[i]=calculatetwoscomplement((int)((int)0xFF & data[iData]),8);
				iData=iData+1;
			}
			else if (dataType[i]=="u12") {

				formattedData[i]=(int)((int)(data[iData+1] & 0xFF) + ((int)(data[iData] & 0xFF) << 8));
				iData=iData+2;
			}
			else if (dataType[i]=="u16") {

				formattedData[i]=(int)((int)(data[iData+1] & 0xFF) + ((int)(data[iData] & 0xFF) << 8));
				iData=iData+2;
			}
			else if (dataType[i]=="i16") {

				formattedData[i]=calculatetwoscomplement((int)((int)(data[iData+1] & 0xFF) + ((int)(data[iData] & 0xFF) << 8)),16);
				iData=iData+2;
			}
		return formattedData;
	}

	private int calculatetwoscomplement(int signedData, int bitLength)
	{
		int newData=signedData;
		if (signedData>=(1<<(bitLength-1))) {
			newData=-((signedData^(int)(Math.pow(2, bitLength)-1))+1);
		}

		return newData;
	}

	protected int getSignalIndex(String signalName) {
		int iSignal=0; //used to be -1, putting to zero ensure it works eventhough it might be wrong SR30
		for (int i=0;i<mSignalNameArray.length;i++) {
			if (signalName==mSignalNameArray[i]) {
				iSignal=i;
			}
		}

		return iSignal;
	}

	protected void interpretdatapacketformat(int nC, byte[] signalid)
	{
		String [] signalNameArray=new String[MAX_NUMBER_OF_SIGNALS];
		String [] signalDataTypeArray=new String[MAX_NUMBER_OF_SIGNALS];
		signalNameArray[0]="TimeStamp";
		signalDataTypeArray[0]="u16";
		int packetSize=2; // Time stamp
		int enabledSensors= 0x00;
		for (int i=0;i<nC;i++) {
			if ((byte)signalid[i]==(byte)0x00)
			{
				if (mShimmerVersion==SHIMMER_SR30 || mShimmerVersion==SHIMMER_3){
					signalNameArray[i+1]="Low Noise Accelerometer X";
					signalDataTypeArray[i+1] = "i16";
					packetSize=packetSize+2;
					enabledSensors= (enabledSensors|Configuration.Shimmer3.SensorBitmap.SENSOR_A_ACCEL_S3);
				} else {
					signalNameArray[i+1]="Accelerometer X";
					signalDataTypeArray[i+1] = "u12";
					packetSize=packetSize+2;
					enabledSensors= (enabledSensors|SENSOR_ACCEL);
				}
			}
			else if ((byte)signalid[i]==(byte)0x01)
			{
				if (mShimmerVersion==SHIMMER_SR30 || mShimmerVersion==SHIMMER_3){
					signalNameArray[i+1]="Low Noise Accelerometer Y";
					signalDataTypeArray[i+1] = "i16";
					packetSize=packetSize+2; 
					enabledSensors= (enabledSensors|Configuration.Shimmer3.SensorBitmap.SENSOR_A_ACCEL_S3);
				} else {
					signalNameArray[i+1]="Accelerometer Y";
					signalDataTypeArray[i+1] = "u12";
					packetSize=packetSize+2;
					enabledSensors= (enabledSensors|SENSOR_ACCEL);
				}
			}
			else if ((byte)signalid[i]==(byte)0x02)
			{
				if (mShimmerVersion==SHIMMER_SR30 || mShimmerVersion==SHIMMER_3){
					signalNameArray[i+1]="Low Noise Accelerometer Z";
					signalDataTypeArray[i+1] = "i16";
					packetSize=packetSize+2;
					enabledSensors= (enabledSensors|Configuration.Shimmer3.SensorBitmap.SENSOR_A_ACCEL_S3);
				} else {
					signalNameArray[i+1]="Accelerometer Z";
					signalDataTypeArray[i+1] = "u12";
					packetSize=packetSize+2;
					enabledSensors= (enabledSensors|SENSOR_ACCEL);
				}
			}
			else if ((byte)signalid[i]==(byte)0x03)
			{

				if (mShimmerVersion==SHIMMER_SR30){
					signalNameArray[i+1]="Gyroscope X";
					signalDataTypeArray[i+1] = "i16";
					packetSize=packetSize+2;
					enabledSensors= (enabledSensors|Configuration.Shimmer3.SensorBitmap.SENSOR_GYRO_S3);
				} else if (mShimmerVersion==SHIMMER_3){
					signalNameArray[i+1]="VSenseBatt"; //should be the battery but this will do for now
					signalDataTypeArray[i+1] = "i16";
					packetSize=packetSize+2;
					enabledSensors= (enabledSensors|Configuration.Shimmer3.SensorBitmap.SENSOR_VBATT_S3);	
				} else {
					signalNameArray[i+1]="Gyroscope X";
					signalDataTypeArray[i+1] = "u12";
					packetSize=packetSize+2;
					enabledSensors= (enabledSensors|SENSOR_GYRO);
				}
			}
			else if ((byte)signalid[i]==(byte)0x04)
			{

				if (mShimmerVersion==SHIMMER_SR30){
					signalNameArray[i+1]="Gyroscope Y";
					signalDataTypeArray[i+1] = "i16";
					packetSize=packetSize+2;
					enabledSensors= (enabledSensors|Configuration.Shimmer3.SensorBitmap.SENSOR_GYRO_S3);
				} else if (mShimmerVersion==SHIMMER_3){
					signalDataTypeArray[i+1] = "i16";
					packetSize=packetSize+2;
					signalNameArray[i+1]="Wide Range Accelerometer X";
					enabledSensors= (enabledSensors|Configuration.Shimmer3.SensorBitmap.SENSOR_D_ACCEL_S3);
				} else {
					signalNameArray[i+1]="Gyroscope Y";
					signalDataTypeArray[i+1] = "u12";
					packetSize=packetSize+2;
					enabledSensors= (enabledSensors|SENSOR_GYRO);
				}
			}
			else if ((byte)signalid[i]==(byte)0x05)
			{
				if (mShimmerVersion==SHIMMER_SR30){
					signalNameArray[i+1]="Gyroscope Z";
					signalDataTypeArray[i+1] = "i16";
					packetSize=packetSize+2;
					enabledSensors= (enabledSensors|Configuration.Shimmer3.SensorBitmap.SENSOR_GYRO_S3);
				} else if (mShimmerVersion==SHIMMER_3){
					signalDataTypeArray[i+1] = "i16";
					packetSize=packetSize+2;
					signalNameArray[i+1]="Wide Range Accelerometer Y";
					enabledSensors= (enabledSensors|Configuration.Shimmer3.SensorBitmap.SENSOR_D_ACCEL_S3);
				} else {
					signalNameArray[i+1]="Gyroscope Z";
					signalDataTypeArray[i+1] = "u12";
					packetSize=packetSize+2;
					enabledSensors= (enabledSensors|SENSOR_GYRO);
				}
			}
			else if ((byte)signalid[i]==(byte)0x06)
			{
				if(mShimmerVersion==SHIMMER_SR30){
					signalNameArray[i+1]="VSenseBatt"; //should be the battery but this will do for now
					signalDataTypeArray[i+1] = "i16";
					packetSize=packetSize+2;
					enabledSensors= (enabledSensors|Configuration.Shimmer3.SensorBitmap.SENSOR_VBATT_S3);	
				} else if (mShimmerVersion==SHIMMER_3){
					signalDataTypeArray[i+1] = "i16";
					packetSize=packetSize+2;
					signalNameArray[i+1]="Wide Range Accelerometer Z";
					enabledSensors= (enabledSensors|Configuration.Shimmer3.SensorBitmap.SENSOR_D_ACCEL_S3);
				} else {
					signalNameArray[i+1]="Magnetometer X";
					signalDataTypeArray[i+1] = "i16";
					packetSize=packetSize+2;
					enabledSensors= (enabledSensors|SENSOR_MAG);
				}


			}
			else if ((byte)signalid[i]==(byte)0x07)
			{
				if(mShimmerVersion==SHIMMER_SR30){
					signalDataTypeArray[i+1] = "i16";
					packetSize=packetSize+2;
					signalNameArray[i+1]="Wide Range Accelerometer X";
					enabledSensors= (enabledSensors|Configuration.Shimmer3.SensorBitmap.SENSOR_D_ACCEL_S3);
				} else if(mShimmerVersion==SHIMMER_3){
					signalNameArray[i+1]="Magnetometer X";
					signalDataTypeArray[i+1] = "i16r";			
					packetSize=packetSize+2;
					enabledSensors= (enabledSensors|Configuration.Shimmer3.SensorBitmap.SENSOR_MAG_S3);
				} else {
					signalDataTypeArray[i+1] = "i16";
					packetSize=packetSize+2;
					signalNameArray[i+1]="Magnetometer Y";
					enabledSensors= (enabledSensors|SENSOR_MAG);
				}


			}
			else if ((byte)signalid[i]==(byte)0x08)
			{	
				if(mShimmerVersion==SHIMMER_SR30){
					signalDataTypeArray[i+1] = "i16";
					packetSize=packetSize+2;
					signalNameArray[i+1]="Wide Range Accelerometer Y";
					enabledSensors= (enabledSensors|Configuration.Shimmer3.SensorBitmap.SENSOR_D_ACCEL_S3);
				} else if(mShimmerVersion==SHIMMER_3){
					signalNameArray[i+1]="Magnetometer Y";
					signalDataTypeArray[i+1] = "i16r";			
					packetSize=packetSize+2;
					enabledSensors= (enabledSensors|Configuration.Shimmer3.SensorBitmap.SENSOR_MAG_S3);
				} else {
					signalDataTypeArray[i+1] = "i16";
					packetSize=packetSize+2;
					signalNameArray[i+1]="Magnetometer Z";
					enabledSensors= (enabledSensors|SENSOR_MAG);
				}

			}
			else if ((byte)signalid[i]==(byte)0x09)
			{
				if(mShimmerVersion==SHIMMER_SR30){
					signalNameArray[i+1]="Wide Range Accelerometer Z";
					signalDataTypeArray[i+1] = "i16";
					packetSize=packetSize+2;
					enabledSensors= (enabledSensors|Configuration.Shimmer3.SensorBitmap.SENSOR_D_ACCEL_S3);
				} else if(mShimmerVersion==SHIMMER_3){
					signalNameArray[i+1]="Magnetometer Z";
					signalDataTypeArray[i+1] = "i16r";			
					packetSize=packetSize+2;
					enabledSensors= (enabledSensors|Configuration.Shimmer3.SensorBitmap.SENSOR_MAG_S3);
				} else {
					signalNameArray[i+1]="ECG RA LL";
					signalDataTypeArray[i+1] = "u12";
					packetSize=packetSize+2;
					enabledSensors= (enabledSensors|SENSOR_ECG);
				}


			}
			else if ((byte)signalid[i]==(byte)0x0A)
			{
				if(mShimmerVersion==SHIMMER_SR30){
					signalNameArray[i+1]="Magnetometer X";
					signalDataTypeArray[i+1] = "i16";			
					packetSize=packetSize+2;
					enabledSensors= (enabledSensors|Configuration.Shimmer3.SensorBitmap.SENSOR_MAG_S3);
				} else if (mShimmerVersion==SHIMMER_3) {
					signalNameArray[i+1]="Gyroscope X";
					signalDataTypeArray[i+1] = "i16r";
					packetSize=packetSize+2;
					enabledSensors= (enabledSensors|Configuration.Shimmer3.SensorBitmap.SENSOR_GYRO_S3);
				} else {

					signalNameArray[i+1]="ECG LA LL";
					signalDataTypeArray[i+1] = "u12";
					packetSize=packetSize+2;
					enabledSensors= (enabledSensors|SENSOR_ECG);
				}
			}
			else if ((byte)signalid[i]==(byte)0x0B)
			{
				if(mShimmerVersion==SHIMMER_SR30){
					signalNameArray[i+1]="Magnetometer Y";
					signalDataTypeArray[i+1] = "i16";			
					packetSize=packetSize+2;
					enabledSensors= (enabledSensors|Configuration.Shimmer3.SensorBitmap.SENSOR_MAG_S3);
				}  else if (mShimmerVersion==SHIMMER_3) {
					signalNameArray[i+1]="Gyroscope Y";
					signalDataTypeArray[i+1] = "i16r";
					packetSize=packetSize+2;
					enabledSensors= (enabledSensors|Configuration.Shimmer3.SensorBitmap.SENSOR_GYRO_S3);
				} else {
					signalNameArray[i+1]="GSR Raw";
					signalDataTypeArray[i+1] = "u16";
					packetSize=packetSize+2;
					enabledSensors= (enabledSensors|SENSOR_GSR);
				}
			}
			else if ((byte)signalid[i]==(byte)0x0C)
			{
				if(mShimmerVersion==SHIMMER_SR30){
					signalNameArray[i+1]="Magnetometer Z";
					signalDataTypeArray[i+1] = "i16";			
					packetSize=packetSize+2;
					enabledSensors= (enabledSensors|Configuration.Shimmer3.SensorBitmap.SENSOR_MAG_S3);
				} else if (mShimmerVersion==SHIMMER_3) {
					signalNameArray[i+1]="Gyroscope Z";
					signalDataTypeArray[i+1] = "i16r";
					packetSize=packetSize+2;
					enabledSensors= (enabledSensors|Configuration.Shimmer3.SensorBitmap.SENSOR_GYRO_S3);
				} else {
					signalNameArray[i+1]="GSR Res";
					signalDataTypeArray[i+1] = "u16";
					packetSize=packetSize+2;
					enabledSensors= (enabledSensors|SENSOR_GSR);
				}
			}
			else if ((byte)signalid[i]==(byte)0x0D)
			{
				if (mShimmerVersion==SHIMMER_3){
					signalNameArray[i+1]="External ADC A7";
					signalDataTypeArray[i+1] = "u12";
					packetSize=packetSize+2;
					enabledSensors= (enabledSensors|SENSOR_EXT_ADC_A7);
				} else{
					signalNameArray[i+1]="EMG";
					signalDataTypeArray[i+1] = "u12";
					packetSize=packetSize+2;
					enabledSensors= (enabledSensors|SENSOR_EMG);
				}
			}
			else if ((byte)signalid[i]==(byte)0x0E)
			{
				if (mShimmerVersion==SHIMMER_3){
					signalNameArray[i+1]="External ADC A6";
					signalDataTypeArray[i+1] = "u12";
					packetSize=packetSize+2;
					enabledSensors= (enabledSensors|SENSOR_EXT_ADC_A6);
				} else{
					signalNameArray[i+1]="Exp Board A0";
					signalDataTypeArray[i+1] = "u12";
					packetSize=packetSize+2;
					enabledSensors= (enabledSensors|SENSOR_EXP_BOARD_A0);
				}
			}
			else if ((byte)signalid[i]==(byte)0x0F)
			{
				if (mShimmerVersion==SHIMMER_3){
					signalNameArray[i+1]="External ADC A15";
					signalDataTypeArray[i+1] = "u12";
					packetSize=packetSize+2;
					enabledSensors= (enabledSensors|SENSOR_EXT_ADC_A15);
				} else{
					signalNameArray[i+1]="Exp Board A7";
					signalDataTypeArray[i+1] = "u12";
					packetSize=packetSize+2;
					enabledSensors= (enabledSensors|SENSOR_EXP_BOARD_A7);
				}
			}
			else if ((byte)signalid[i]==(byte)0x10)
			{
				if (mShimmerVersion==SHIMMER_3){
					signalNameArray[i+1]="Internal ADC A1";
					signalDataTypeArray[i+1] = "u12";
					packetSize=packetSize+2;
					enabledSensors= (enabledSensors|SENSOR_INT_ADC_A1);
				} else {
					signalNameArray[i+1]="Bridge Amplifier High";
					signalDataTypeArray[i+1] = "u12";
					packetSize=packetSize+2;
					enabledSensors= (enabledSensors|SENSOR_BRIDGE_AMP);
				}
			}

			else if ((byte)signalid[i]==(byte)0x11)
			{
				if (mShimmerVersion==SHIMMER_3){
					signalNameArray[i+1]="Internal ADC A12";
					signalDataTypeArray[i+1] = "u12";
					packetSize=packetSize+2;
					enabledSensors= (enabledSensors|SENSOR_INT_ADC_A12);
				} else {
					signalNameArray[i+1]="Bridge Amplifier Low";
					signalDataTypeArray[i+1] = "u12";
					packetSize=packetSize+2;
					enabledSensors= (enabledSensors|SENSOR_BRIDGE_AMP);
				}
			}
			else if ((byte)signalid[i]==(byte)0x12)
			{
				if (mShimmerVersion==SHIMMER_3){
					signalNameArray[i+1]="Internal ADC A13";
					signalDataTypeArray[i+1] = "u12";
					packetSize=packetSize+2;
					enabledSensors= (enabledSensors|SENSOR_INT_ADC_A13);
				} else {
					signalNameArray[i+1]="Heart Rate";
					if (mFWMajorVersion==0 && mFWMinorVersion==1){
						signalDataTypeArray[i+1] = "u8";
						packetSize=packetSize+1;
					} else {
						signalDataTypeArray[i+1] = "u16"; 
						packetSize=packetSize+2;
					}
					enabledSensors= (enabledSensors|SENSOR_HEART);
				}
			}
			else if ((byte)signalid[i]==(byte)0x13)
			{
				if (mShimmerVersion==SHIMMER_3){
					signalNameArray[i+1]="Internal ADC A14";
					signalDataTypeArray[i+1] = "u12";
					packetSize=packetSize+2;
					enabledSensors= (enabledSensors|SENSOR_INT_ADC_A14);
				}
			}
			else if ((byte)signalid[i]==(byte)0x1A){
				if (mShimmerVersion==SHIMMER_3){
					signalNameArray[i+1]="Temperature";
					signalDataTypeArray[i+1] = "u16r";
					packetSize=packetSize+2;
					enabledSensors= (enabledSensors|SENSOR_BMP180);
				}
			}
			else if ((byte)signalid[i]==(byte)0x1B){
				if (mShimmerVersion==SHIMMER_3){
					signalNameArray[i+1]="Pressure";
					signalDataTypeArray[i+1] = "u24r";
					packetSize=packetSize+3;
					enabledSensors= (enabledSensors|SENSOR_BMP180);
				}
			}
			else if ((byte)signalid[i]==(byte)0x1C){
				if (mShimmerVersion==SHIMMER_3){
					signalNameArray[i+1]="GSR Raw";
					signalDataTypeArray[i+1] = "u16";
					packetSize=packetSize+2;
					enabledSensors= (enabledSensors|SENSOR_GSR);
				}
			}
			else if ((byte)signalid[i]==(byte)0x1D){
				if (mShimmerVersion==SHIMMER_3){
					signalNameArray[i+1]="EXG1 STATUS";
					signalDataTypeArray[i+1] = "u8";
					packetSize=packetSize+1;

				}
			}
			else if ((byte)signalid[i]==(byte)0x1E){
				if (mShimmerVersion==SHIMMER_3){
					signalNameArray[i+1]="EXG1 24Bit CH1";
					signalDataTypeArray[i+1] = "i24r";
					packetSize=packetSize+3;
					enabledSensors= (enabledSensors|SENSOR_EXG1_24BIT);
				}
			}
			else if ((byte)signalid[i]==(byte)0x1F){
				if (mShimmerVersion==SHIMMER_3){
					signalNameArray[i+1]="EXG1 24Bit CH2";
					signalDataTypeArray[i+1] = "i24r";
					packetSize=packetSize+3;
					enabledSensors= (enabledSensors|SENSOR_EXG1_24BIT);
				}
			}

			else if ((byte)signalid[i]==(byte)0x20){
				if (mShimmerVersion==SHIMMER_3){
					signalNameArray[i+1]="EXG2 STATUS";
					signalDataTypeArray[i+1] = "u8";
					packetSize=packetSize+1;

				}
			}
			else if ((byte)signalid[i]==(byte)0x21){
				if (mShimmerVersion==SHIMMER_3){
					signalNameArray[i+1]="EXG2 24Bit CH1";
					signalDataTypeArray[i+1] = "i24r";
					packetSize=packetSize+3;
					enabledSensors= (enabledSensors|SENSOR_EXG2_24BIT);
				}
			}
			else if ((byte)signalid[i]==(byte)0x22){
				if (mShimmerVersion==SHIMMER_3){
					signalNameArray[i+1]="EXG2 24Bit CH2";
					signalDataTypeArray[i+1] = "i24r";
					packetSize=packetSize+3;
					enabledSensors= (enabledSensors|SENSOR_EXG2_24BIT);
				}
			}

			else if ((byte)signalid[i]==(byte)0x23){
				if (mShimmerVersion==SHIMMER_3){
					signalNameArray[i+1]="EXG1 16Bit CH1";
					signalDataTypeArray[i+1] = "i16r";
					packetSize=packetSize+2;
					enabledSensors= (enabledSensors|SENSOR_EXG1_16BIT);
				}
			}
			else if ((byte)signalid[i]==(byte)0x24){
				if (mShimmerVersion==SHIMMER_3){
					signalNameArray[i+1]="EXG1 16Bit CH2";
					signalDataTypeArray[i+1] = "i16r";
					packetSize=packetSize+2;
					enabledSensors= (enabledSensors|SENSOR_EXG1_16BIT);
				}
			}

			else if ((byte)signalid[i]==(byte)0x25){
				if (mShimmerVersion==SHIMMER_3){
					signalNameArray[i+1]="EXG2 16Bit CH1";
					signalDataTypeArray[i+1] = "i16r";
					packetSize=packetSize+2;
					enabledSensors= (enabledSensors|SENSOR_EXG2_16BIT);
				}
			}
			else if ((byte)signalid[i]==(byte)0x26){
				if (mShimmerVersion==SHIMMER_3){
					signalNameArray[i+1]="EXG2 16Bit CH2";
					signalDataTypeArray[i+1] = "i16r";
					packetSize=packetSize+2;
					enabledSensors= (enabledSensors|SENSOR_EXG2_16BIT);
				}
			}
			else if ((byte)signalid[i]==(byte)0x27)
			{
				if (mShimmerVersion==SHIMMER_3){
					signalNameArray[i+1]="Bridge Amplifier High";
					signalDataTypeArray[i+1] = "u12";
					packetSize=packetSize+2;
					enabledSensors= (enabledSensors|SENSOR_BRIDGE_AMP);
				} 
			}
			else if ((byte)signalid[i]==(byte)0x28)
			{
				if (mShimmerVersion==SHIMMER_3){
					signalNameArray[i+1]="Bridge Amplifier Low";
					signalDataTypeArray[i+1] = "u12";
					packetSize=packetSize+2;
					enabledSensors= (enabledSensors|SENSOR_BRIDGE_AMP);
				} 
			}
			else
			{
				signalNameArray[i+1]=Byte.toString(signalid[i]);
				signalDataTypeArray[i+1] = "u12";
				packetSize=packetSize+2;
			}

		}
		mSignalNameArray=signalNameArray;
		mSignalDataTypeArray=signalDataTypeArray;
		mPacketSize=packetSize;
	}

	protected void retrievepressurecalibrationparametersfrompacket(byte[] pressureResoRes, int packetType)
	{
		if (packetType == BMP180_CALIBRATION_COEFFICIENTS_RESPONSE){
			AC1 = calculatetwoscomplement((int)((int)(pressureResoRes[1] & 0xFF) + ((int)(pressureResoRes[0] & 0xFF) << 8)),16);
			AC2 = calculatetwoscomplement((int)((int)(pressureResoRes[3] & 0xFF) + ((int)(pressureResoRes[2] & 0xFF) << 8)),16);
			AC3 = calculatetwoscomplement((int)((int)(pressureResoRes[5] & 0xFF) + ((int)(pressureResoRes[4] & 0xFF) << 8)),16);
			AC4 = (int)((int)(pressureResoRes[7] & 0xFF) + ((int)(pressureResoRes[6] & 0xFF) << 8));
			AC5 = (int)((int)(pressureResoRes[9] & 0xFF) + ((int)(pressureResoRes[8] & 0xFF) << 8));
			AC6 = (int)((int)(pressureResoRes[11] & 0xFF) + ((int)(pressureResoRes[10] & 0xFF) << 8));
			B1 = calculatetwoscomplement((int)((int)(pressureResoRes[13] & 0xFF) + ((int)(pressureResoRes[12] & 0xFF) << 8)),16);
			B2 = calculatetwoscomplement((int)((int)(pressureResoRes[15] & 0xFF) + ((int)(pressureResoRes[14] & 0xFF) << 8)),16);
			MB = calculatetwoscomplement((int)((int)(pressureResoRes[17] & 0xFF) + ((int)(pressureResoRes[16] & 0xFF) << 8)),16);
			MC = calculatetwoscomplement((int)((int)(pressureResoRes[19] & 0xFF) + ((int)(pressureResoRes[18] & 0xFF) << 8)),16);
			MD = calculatetwoscomplement((int)((int)(pressureResoRes[21] & 0xFF) + ((int)(pressureResoRes[20] & 0xFF) << 8)),16);
		}



	}
	/**
	 * Should only be used when Shimmer is Connected and Initialized
	 */
	public static BiMap<String,String> generateBiMapSensorIDtoSensorName(int shimmerVersion){
		BiMap<String, String> sensorBitmaptoName =null;  
		if (shimmerVersion != SHIMMER_2R){
			final Map<String, String> tempSensorBMtoName = new HashMap<String, String>();  
			tempSensorBMtoName.put(Integer.toString(SENSOR_GYRO), "Gyroscope");  
			tempSensorBMtoName.put(Integer.toString(SENSOR_MAG), "Magnetometer");  
			tempSensorBMtoName.put(Integer.toString(SENSOR_GSR), "GSR");  
			tempSensorBMtoName.put(Integer.toString(SENSOR_EXP_BOARD_A7), "Exp Board A7");
			tempSensorBMtoName.put(Integer.toString(SENSOR_EXP_BOARD_A0), "Exp Board A0");
			tempSensorBMtoName.put(Integer.toString(SENSOR_EXP_BOARD), "Exp Board");
			tempSensorBMtoName.put(Integer.toString(SENSOR_BRIDGE_AMP), "Bridge Amplifier");
			tempSensorBMtoName.put(Integer.toString(SENSOR_HEART), "Heart Rate");  
			tempSensorBMtoName.put(Integer.toString(SENSOR_BATT), "Battery Voltage");
			tempSensorBMtoName.put(Integer.toString(SENSOR_EXT_ADC_A7), "External ADC A7");  
			tempSensorBMtoName.put(Integer.toString(SENSOR_EXT_ADC_A6), "External ADC A6");  
			tempSensorBMtoName.put(Integer.toString(SENSOR_EXT_ADC_A15), "External ADC A15");
			tempSensorBMtoName.put(Integer.toString(SENSOR_INT_ADC_A1), "Internal ADC A1");
			tempSensorBMtoName.put(Integer.toString(SENSOR_INT_ADC_A12), "Internal ADC A12");
			tempSensorBMtoName.put(Integer.toString(SENSOR_INT_ADC_A13), "Internal ADC A13");
			tempSensorBMtoName.put(Integer.toString(SENSOR_INT_ADC_A14), "Internal ADC A14");
			tempSensorBMtoName.put(Integer.toString(SENSOR_BMP180), "Pressure");
			tempSensorBMtoName.put(Integer.toString(SENSOR_ACCEL), "Low Noise Accelerometer");  
			tempSensorBMtoName.put(Integer.toString(SENSOR_DACCEL), "Wide Range Accelerometer");
			tempSensorBMtoName.put(Integer.toString(SENSOR_EXG1_24BIT), "EXG1");  
			tempSensorBMtoName.put(Integer.toString(SENSOR_EXG2_24BIT), "EXG2");
			tempSensorBMtoName.put(Integer.toString(SENSOR_EXG1_16BIT), "EXG1 16Bit");  
			tempSensorBMtoName.put(Integer.toString(SENSOR_EXG2_16BIT), "EXG2 16Bit");
			sensorBitmaptoName = ImmutableBiMap.copyOf(Collections.unmodifiableMap(tempSensorBMtoName));
		} else {
			final Map<String, String> tempSensorBMtoName = new HashMap<String, String>();  
			tempSensorBMtoName.put(Integer.toString(SENSOR_ACCEL), "Accelerometer");
			tempSensorBMtoName.put(Integer.toString(SENSOR_GYRO), "Gyroscope");  
			tempSensorBMtoName.put(Integer.toString(SENSOR_MAG), "Magnetometer");  
			tempSensorBMtoName.put(Integer.toString(SENSOR_EMG), "EMG");  
			tempSensorBMtoName.put(Integer.toString(SENSOR_ECG), "ECG");  
			tempSensorBMtoName.put(Integer.toString(SENSOR_GSR), "GSR");  
			tempSensorBMtoName.put(Integer.toString(SENSOR_EXP_BOARD_A7), "Exp Board A7");
			tempSensorBMtoName.put(Integer.toString(SENSOR_EXP_BOARD_A0), "Exp Board A0");
			tempSensorBMtoName.put(Integer.toString(SENSOR_EXP_BOARD), "Exp Board");
			tempSensorBMtoName.put(Integer.toString(SENSOR_BRIDGE_AMP), "Bridge Amplifier");
			tempSensorBMtoName.put(Integer.toString(SENSOR_HEART), "Heart Rate");  
			tempSensorBMtoName.put(Integer.toString(SENSOR_BATT), "Battery Voltage");
			tempSensorBMtoName.put(Integer.toString(SENSOR_EXT_ADC_A7), "External ADC A7");  
			tempSensorBMtoName.put(Integer.toString(SENSOR_EXT_ADC_A6), "External ADC A6");  
			tempSensorBMtoName.put(Integer.toString(SENSOR_EXT_ADC_A15), "External ADC A15");
			tempSensorBMtoName.put(Integer.toString(SENSOR_INT_ADC_A1), "Internal ADC A1");
			tempSensorBMtoName.put(Integer.toString(SENSOR_INT_ADC_A12), "Internal ADC A12");
			tempSensorBMtoName.put(Integer.toString(SENSOR_INT_ADC_A13), "Internal ADC A13");
			tempSensorBMtoName.put(Integer.toString(SENSOR_INT_ADC_A14), "Internal ADC A14");
			sensorBitmaptoName = ImmutableBiMap.copyOf(Collections.unmodifiableMap(tempSensorBMtoName));
		}
		return sensorBitmaptoName;
	}

	/**
	 * Should only be used when Shimmer is Connected and Initialized
	 */
	public void generateBiMapSensorIDtoSensorName(){
		if (mShimmerVersion != -1){
			if (mShimmerVersion != SHIMMER_2R){
				final Map<String, String> tempSensorBMtoName = new HashMap<String, String>();  
				tempSensorBMtoName.put(Integer.toString(SENSOR_BMP180), "Pressure");
				tempSensorBMtoName.put(Integer.toString(SENSOR_GYRO), "Gyroscope");  
				tempSensorBMtoName.put(Integer.toString(SENSOR_MAG), "Magnetometer");  
				tempSensorBMtoName.put(Integer.toString(SHIMMER3_SENSOR_ECG), "ECG");  
				tempSensorBMtoName.put(Integer.toString(SHIMMER3_SENSOR_EMG), "EMG");  
				tempSensorBMtoName.put(Integer.toString(SENSOR_GSR), "GSR");  
				tempSensorBMtoName.put(Integer.toString(SENSOR_EXP_BOARD_A7), "Exp Board A7");
				tempSensorBMtoName.put(Integer.toString(SENSOR_EXP_BOARD_A0), "Exp Board A0");
				tempSensorBMtoName.put(Integer.toString(SENSOR_EXP_BOARD), "Exp Board");
				tempSensorBMtoName.put(Integer.toString(SENSOR_BRIDGE_AMP), "Bridge Amplifier");
				tempSensorBMtoName.put(Integer.toString(SENSOR_HEART), "Heart Rate");  
				tempSensorBMtoName.put(Integer.toString(SENSOR_BATT), "Battery Voltage");
				tempSensorBMtoName.put(Integer.toString(SENSOR_EXT_ADC_A7), "External ADC A7");  
				tempSensorBMtoName.put(Integer.toString(SENSOR_EXT_ADC_A6), "External ADC A6");  
				tempSensorBMtoName.put(Integer.toString(SENSOR_EXT_ADC_A15), "External ADC A15");
				tempSensorBMtoName.put(Integer.toString(SENSOR_INT_ADC_A1), "Internal ADC A1");
				tempSensorBMtoName.put(Integer.toString(SENSOR_INT_ADC_A12), "Internal ADC A12");
				tempSensorBMtoName.put(Integer.toString(SENSOR_INT_ADC_A13), "Internal ADC A13");
				tempSensorBMtoName.put(Integer.toString(SENSOR_INT_ADC_A14), "Internal ADC A14");
				tempSensorBMtoName.put(Integer.toString(SENSOR_ACCEL), "Low Noise Accelerometer");
				tempSensorBMtoName.put(Integer.toString(SENSOR_DACCEL), "Wide Range Accelerometer");
				mSensorBitmaptoName = ImmutableBiMap.copyOf(Collections.unmodifiableMap(tempSensorBMtoName));


			} else {
				final Map<String, String> tempSensorBMtoName = new HashMap<String, String>();  
				tempSensorBMtoName.put(Integer.toString(SENSOR_ACCEL), "Accelerometer");
				tempSensorBMtoName.put(Integer.toString(SENSOR_GYRO), "Gyroscope");  
				tempSensorBMtoName.put(Integer.toString(SENSOR_MAG), "Magnetometer");
				tempSensorBMtoName.put(Integer.toString(SENSOR_ECG), "ECG");  
				tempSensorBMtoName.put(Integer.toString(SENSOR_EMG), "EMG");  
				tempSensorBMtoName.put(Integer.toString(SENSOR_GSR), "GSR");  
				tempSensorBMtoName.put(Integer.toString(SENSOR_EXP_BOARD_A7), "Exp Board A7");
				tempSensorBMtoName.put(Integer.toString(SENSOR_EXP_BOARD_A0), "Exp Board A0");
				tempSensorBMtoName.put(Integer.toString(SENSOR_EXP_BOARD), "Exp Board");
				tempSensorBMtoName.put(Integer.toString(SENSOR_BRIDGE_AMP), "Bridge Amplifier");
				tempSensorBMtoName.put(Integer.toString(SENSOR_HEART), "Heart Rate");  
				tempSensorBMtoName.put(Integer.toString(SENSOR_BATT), "Battery Voltage");
				tempSensorBMtoName.put(Integer.toString(SENSOR_EXT_ADC_A7), "External ADC A7");  
				tempSensorBMtoName.put(Integer.toString(SENSOR_EXT_ADC_A6), "External ADC A6");  
				tempSensorBMtoName.put(Integer.toString(SENSOR_EXT_ADC_A15), "External ADC A15");
				tempSensorBMtoName.put(Integer.toString(SENSOR_INT_ADC_A1), "Internal ADC A1");
				tempSensorBMtoName.put(Integer.toString(SENSOR_INT_ADC_A12), "Internal ADC A12");
				tempSensorBMtoName.put(Integer.toString(SENSOR_INT_ADC_A13), "Internal ADC A13");
				tempSensorBMtoName.put(Integer.toString(SENSOR_INT_ADC_A14), "Internal ADC A14");
				mSensorBitmaptoName = ImmutableBiMap.copyOf(Collections.unmodifiableMap(tempSensorBMtoName));
			}

		}
	}



	public String[] getListofEnabledSensorSignals(){
		List<String> listofSignals = new ArrayList<String>();
		String[] enabledSignals; 
		if (mShimmerVersion!=SHIMMER_3){
			listofSignals.add("Timestamp");
			if (((mEnabledSensors & 0xFF)& SENSOR_ACCEL) > 0){
				listofSignals.add("Accelerometer X");
				listofSignals.add("Accelerometer Y");
				listofSignals.add("Accelerometer Z");
			}
			if (((mEnabledSensors & 0xFF)& SENSOR_GYRO) > 0) {
				listofSignals.add("Gyroscope X");
				listofSignals.add("Gyroscope Y");
				listofSignals.add("Gyroscope Z");
			}
			if (((mEnabledSensors & 0xFF)& SENSOR_MAG) > 0) {
				listofSignals.add("Magnetometer X");
				listofSignals.add("Magnetometer Y");
				listofSignals.add("Magnetometer Z");
			}
			if (((mEnabledSensors & 0xFF) & SENSOR_GSR) > 0) {
				listofSignals.add("GSR");
			}
			if (((mEnabledSensors & 0xFF) & SENSOR_ECG) > 0) {
				listofSignals.add("ECG RA-LL");
				listofSignals.add("ECG LA-LL");
			}
			if (((mEnabledSensors & 0xFF) & SENSOR_EMG) > 0) {
				listofSignals.add("EMG");
			}
			if (((mEnabledSensors & 0xFF00) & SENSOR_BRIDGE_AMP) > 0) {
				listofSignals.add("Bridge Amplifier High");
				listofSignals.add("Bridge Amplifier Low");
			}
			if (((mEnabledSensors & 0xFF00) & SENSOR_HEART) > 0) {
				listofSignals.add("Heart Rate");
			}
			if ((((mEnabledSensors & 0xFF) & SENSOR_EXP_BOARD_A0) > 0) && getPMux() == 0) {
				listofSignals.add("ExpBoard A0");
			}
			if ((((mEnabledSensors & 0xFF) & SENSOR_EXP_BOARD_A7) > 0 && getPMux() == 0)) {
				listofSignals.add("ExpBoard A7");
			}
			if (((mEnabledSensors & 0xFFFF) & SENSOR_BATT) > 0) {
				listofSignals.add("VSenseBatt");
				listofSignals.add("VSenseReg");
			}
			if (((mEnabledSensors & 0xFF)& SENSOR_ACCEL) > 0 && ((mEnabledSensors & 0xFF)& SENSOR_GYRO) > 0 && ((mEnabledSensors & 0xFF)& SENSOR_MAG) > 0 && mOrientationEnabled){
				listofSignals.add("Axis Angle A");
				listofSignals.add("Axis Angle X");
				listofSignals.add("Axis Angle Y");
				listofSignals.add("Axis Angle Z");
			}
			if (((mEnabledSensors & 0xFF)& SENSOR_ACCEL) > 0 && ((mEnabledSensors & 0xFF)& SENSOR_GYRO) > 0 && ((mEnabledSensors & 0xFF)& SENSOR_MAG) > 0 && mOrientationEnabled){
				listofSignals.add("Quaternion 0");
				listofSignals.add("Quaternion 1");
				listofSignals.add("Quaternion 2");
				listofSignals.add("Quaternion 3");
			}

		} else {
			listofSignals.add("Timestamp");
			if ((mEnabledSensors & SENSOR_ACCEL) >0){
				listofSignals.add("Low Noise Accelerometer X");
				listofSignals.add("Low Noise Accelerometer Y");
				listofSignals.add("Low Noise Accelerometer Z");
			}
			if ((mEnabledSensors& SENSOR_DACCEL) >0){
				listofSignals.add("Wide Range Accelerometer X");
				listofSignals.add("Wide Range Accelerometer Y");
				listofSignals.add("Wide Range Accelerometer Z");
			}
			if (((mEnabledSensors & 0xFF)& SENSOR_GYRO) > 0) {
				listofSignals.add("Gyroscope X");
				listofSignals.add("Gyroscope Y");
				listofSignals.add("Gyroscope Z");
			}
			if (((mEnabledSensors & 0xFF)& SENSOR_MAG) > 0) {
				listofSignals.add("Magnetometer X");
				listofSignals.add("Magnetometer Y");
				listofSignals.add("Magnetometer Z");
			} 
			if (((mEnabledSensors & 0xFFFF) & SENSOR_BATT) > 0) {
				listofSignals.add("VSenseBatt");
			}
			if (((mEnabledSensors & 0xFFFFFF)& SENSOR_EXT_ADC_A15) > 0) {
				listofSignals.add("External ADC A15");
			}
			if (((mEnabledSensors & 0xFFFFFF)& SENSOR_EXT_ADC_A7) > 0) {
				listofSignals.add("External ADC A7");
			}
			if (((mEnabledSensors & 0xFFFFFF)& SENSOR_EXT_ADC_A6) > 0) {
				listofSignals.add("External ADC A6");
			}
			if (((mEnabledSensors & 0xFFFFFF)& SENSOR_INT_ADC_A1) > 0) {
				listofSignals.add("Internal ADC A1");
			}
			if (((mEnabledSensors & 0xFFFFFF)& SENSOR_INT_ADC_A12) > 0) {
				listofSignals.add("Internal ADC A12");
			}
			if (((mEnabledSensors & 0xFFFFFF)& SENSOR_INT_ADC_A13) > 0) {
				listofSignals.add("Internal ADC A13");
			}
			if (((mEnabledSensors & 0xFFFFFF)& SENSOR_INT_ADC_A14) > 0) {
				listofSignals.add("Internal ADC A14");
			}
			if ((((mEnabledSensors & 0xFF)& SENSOR_ACCEL) > 0 || ((mEnabledSensors & 0xFFFF)& SENSOR_DACCEL) > 0)&& ((mEnabledSensors & 0xFF)& SENSOR_GYRO) > 0 && ((mEnabledSensors & 0xFF)& SENSOR_MAG) > 0 && mOrientationEnabled){
				listofSignals.add("Axis Angle A");
				listofSignals.add("Axis Angle X");
				listofSignals.add("Axis Angle Y");
				listofSignals.add("Axis Angle Z");
			}
			if ((((mEnabledSensors & 0xFF)& SENSOR_ACCEL) > 0 || ((mEnabledSensors & 0xFFFF)& SENSOR_DACCEL) > 0) && ((mEnabledSensors & 0xFF)& SENSOR_GYRO) > 0 && ((mEnabledSensors & 0xFF)& SENSOR_MAG) > 0 && mOrientationEnabled){
				listofSignals.add("Quaternion 0");
				listofSignals.add("Quaternion 1");
				listofSignals.add("Quaternion 2");
				listofSignals.add("Quaternion 3");
			}
			if ((mEnabledSensors & SENSOR_BMP180)>0){
				listofSignals.add("Pressure");
				listofSignals.add("Temperature");
			}
			if ((mEnabledSensors & SENSOR_GSR)>0){
				listofSignals.add("GSR");
			}
			if (((mEnabledSensors & SENSOR_EXG1_24BIT)>0)|| ((mEnabledSensors & SENSOR_EXG1_16BIT)>0)){
				listofSignals.add("EXG1 STATUS");
			}
			if (((mEnabledSensors & SENSOR_EXG2_24BIT)>0)|| ((mEnabledSensors & SENSOR_EXG2_16BIT)>0)){
				listofSignals.add("EXG2 STATUS");
			}
			if ((mEnabledSensors & SENSOR_EXG1_24BIT)>0){
				if (isEXGUsingDefaultECGConfiguration()){
					listofSignals.add("ECG LL-RA");
					listofSignals.add("ECG LA-RA");
				}
				else if (isEXGUsingDefaultEMGConfiguration()){
					listofSignals.add("EMG CH1");
					listofSignals.add("EMG CH2");
				} else {
					listofSignals.add("EXG1 CH1");
					listofSignals.add("EXG1 CH2");
				}

			}
			if ((mEnabledSensors & SENSOR_EXG2_24BIT)>0){
				if (isEXGUsingDefaultECGConfiguration()){
					listofSignals.add("EXG2 CH1");
					listofSignals.add("ECG Vx-RL");
				}
				else if (isEXGUsingDefaultEMGConfiguration()){
					listofSignals.add("EXG2 CH1");
					listofSignals.add("EXG2 CH2");
				} else {
					listofSignals.add("EXG2 CH1");
					listofSignals.add("EXG2 CH2");
				}
			}
			if ((mEnabledSensors & SENSOR_EXG1_16BIT)>0){
				if (isEXGUsingDefaultECGConfiguration()){
					listofSignals.add("ECG LL-RA");
					listofSignals.add("ECG LA-RA");
				}
				else if (isEXGUsingDefaultEMGConfiguration()){
					listofSignals.add("EMG CH1");
					listofSignals.add("EMG CH2");
				} else {
					listofSignals.add("EXG1 CH1 16Bit");
					listofSignals.add("EXG1 CH2 16Bit");
				}
			}
			if ((mEnabledSensors & SENSOR_EXG2_16BIT)>0){
				if (isEXGUsingDefaultECGConfiguration()){
					listofSignals.add("EXG2 CH1");
					listofSignals.add("ECG Vx-RL");
				}
				else if (isEXGUsingDefaultEMGConfiguration()){
					listofSignals.add("EXG2 CH1 16Bit");
					listofSignals.add("EXG2 CH2 16Bit");
				} else {
					listofSignals.add("EXG2 CH1 16Bit");
					listofSignals.add("EXG2 CH2 16Bit");
				}
			}
			if (((mEnabledSensors & 0xFF00) & SENSOR_BRIDGE_AMP) > 0) {
				listofSignals.add("Bridge Amplifier High");
				listofSignals.add("Bridge Amplifier Low");
			}
		}
		enabledSignals = listofSignals.toArray(new String[listofSignals.size()]);
		return enabledSignals;
	}	

	protected void retrievebiophysicalcalibrationparametersfrompacket(byte[] bufferCalibrationParameters, int packetType)
	{
		if (packetType == ECG_CALIBRATION_RESPONSE){
			if (bufferCalibrationParameters[0]==-1 && bufferCalibrationParameters[1] == -1 && bufferCalibrationParameters[2] == -1 && bufferCalibrationParameters[3]==-1){
				mDefaultCalibrationParametersECG = true;
			} else {
				mDefaultCalibrationParametersECG = false;
				OffsetECGLALL=(double)((bufferCalibrationParameters[0]&0xFF)<<8)+(bufferCalibrationParameters[1]&0xFF);
				GainECGLALL=(double)((bufferCalibrationParameters[2]&0xFF)<<8)+(bufferCalibrationParameters[3]&0xFF);
				OffsetECGRALL=(double)((bufferCalibrationParameters[4]&0xFF)<<8)+(bufferCalibrationParameters[5]&0xFF);
				GainECGRALL=(double)((bufferCalibrationParameters[6]&0xFF)<<8)+(bufferCalibrationParameters[7]&0xFF);
			}	
		}

		if (packetType == EMG_CALIBRATION_RESPONSE){

			if (bufferCalibrationParameters[0]==-1 && bufferCalibrationParameters[1] == -1 && bufferCalibrationParameters[2] == -1 && bufferCalibrationParameters[3]==-1){
				mDefaultCalibrationParametersEMG = true;
			} else {
				mDefaultCalibrationParametersEMG = false;
				OffsetEMG=(double)((bufferCalibrationParameters[0]&0xFF)<<8)+(bufferCalibrationParameters[1]&0xFF);
				GainEMG=(double)((bufferCalibrationParameters[2]&0xFF)<<8)+(bufferCalibrationParameters[3]&0xFF);
			}
		}

	}

	protected void retrievekinematiccalibrationparametersfrompacket(byte[] bufferCalibrationParameters, int packetType)
	{
		if (packetType==ACCEL_CALIBRATION_RESPONSE || packetType==LSM303DLHC_ACCEL_CALIBRATION_RESPONSE || packetType==GYRO_CALIBRATION_RESPONSE || packetType==MAG_CALIBRATION_RESPONSE ){
			String[] dataType={"i16","i16","i16","i16","i16","i16","i8","i8","i8","i8","i8","i8","i8","i8","i8"}; 
			int[] formattedPacket=formatdatapacketreverse(bufferCalibrationParameters,dataType); // using the datatype the calibration parameters are converted
			double[] AM=new double[9];
			for (int i=0;i<9;i++)
			{
				AM[i]=((double)formattedPacket[6+i])/100;
			}

			double[][] AlignmentMatrix = {{AM[0],AM[1],AM[2]},{AM[3],AM[4],AM[5]},{AM[6],AM[7],AM[8]}}; 				
			double[][] SensitivityMatrix = {{formattedPacket[3],0,0},{0,formattedPacket[4],0},{0,0,formattedPacket[5]}}; 
			double[][] OffsetVector = {{formattedPacket[0]},{formattedPacket[1]},{formattedPacket[2]}};


			if (packetType==ACCEL_CALIBRATION_RESPONSE && SensitivityMatrix[0][0]!=-1) {   //used to be 65535 but changed to -1 as we are now using i16
				mDefaultCalibrationParametersAccel = false;
				AlignmentMatrixAccel = AlignmentMatrix;
				OffsetVectorAccel = OffsetVector;
				SensitivityMatrixAccel = SensitivityMatrix;
			} else if(packetType==ACCEL_CALIBRATION_RESPONSE && SensitivityMatrix[0][0]==-1){
				mDefaultCalibrationParametersAccel = true;
				if (mShimmerVersion!=3){
					AlignmentMatrixAccel = AlignmentMatrixAccelShimmer2;
					OffsetVectorAccel = OffsetVectorAccelShimmer2;
					if (getAccelRange()==0){
						SensitivityMatrixAccel = SensitivityMatrixAccel1p5gShimmer2; 
					} else if (getAccelRange()==1){
						SensitivityMatrixAccel = SensitivityMatrixAccel2gShimmer2; 
					} else if (getAccelRange()==2){
						SensitivityMatrixAccel = SensitivityMatrixAccel4gShimmer2; 
					} else if (getAccelRange()==3){
						SensitivityMatrixAccel = SensitivityMatrixAccel6gShimmer2; 
					}
				} else {
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

			if (packetType==LSM303DLHC_ACCEL_CALIBRATION_RESPONSE && SensitivityMatrix[0][0]!=-1) {   //used to be 65535 but changed to -1 as we are now using i16
				mDefaultCalibrationParametersDigitalAccel = false;
				AlignmentMatrixAccel2 = AlignmentMatrix;
				OffsetVectorAccel2 = OffsetVector;
				SensitivityMatrixAccel2 = SensitivityMatrix;
			} else if(packetType==LSM303DLHC_ACCEL_CALIBRATION_RESPONSE  && SensitivityMatrix[0][0]==-1){
				mDefaultCalibrationParametersDigitalAccel = true;
				if (getAccelRange()==0){
					SensitivityMatrixAccel2 = SensitivityMatrixWideRangeAccel2gShimmer3;
					AlignmentMatrixAccel2 = AlignmentMatrixWideRangeAccelShimmer3;
					OffsetVectorAccel2 = OffsetVectorWideRangeAccelShimmer3;
				} else if (getAccelRange()==1){
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
				}
			}
			if (packetType==GYRO_CALIBRATION_RESPONSE && SensitivityMatrix[0][0]!=-1) {
				mDefaultCalibrationParametersGyro = false;
				AlignmentMatrixGyro = AlignmentMatrix;
				OffsetVectorGyro = OffsetVector;
				SensitivityMatrixGyro = SensitivityMatrix;
				SensitivityMatrixGyro[0][0] = SensitivityMatrixGyro[0][0]/100;
				SensitivityMatrixGyro[1][1] = SensitivityMatrixGyro[1][1]/100;
				SensitivityMatrixGyro[2][2] = SensitivityMatrixGyro[2][2]/100;

			} else if(packetType==GYRO_CALIBRATION_RESPONSE && SensitivityMatrix[0][0]==-1){
				mDefaultCalibrationParametersGyro = true;
				if (mShimmerVersion!=3){
					AlignmentMatrixGyro = AlignmentMatrixGyroShimmer2;
					OffsetVectorGyro = OffsetVectorGyroShimmer2;
					SensitivityMatrixGyro = SensitivityMatrixGyroShimmer2;	
				} else {
					if (mGyroRange==0){
						SensitivityMatrixGyro = SensitivityMatrixGyro250dpsShimmer3;

					} else if (mGyroRange==1){
						SensitivityMatrixGyro = SensitivityMatrixGyro500dpsShimmer3;

					} else if (mGyroRange==2){
						SensitivityMatrixGyro = SensitivityMatrixGyro1000dpsShimmer3;

					} else if (mGyroRange==3){
						SensitivityMatrixGyro = SensitivityMatrixGyro2000dpsShimmer3;
					}
					AlignmentMatrixGyro = AlignmentMatrixGyroShimmer3;
					OffsetVectorGyro = OffsetVectorGyroShimmer3;
				}
			} 
			if (packetType==MAG_CALIBRATION_RESPONSE && SensitivityMatrix[0][0]!=-1) {
				mDefaultCalibrationParametersMag = false;
				AlignmentMatrixMag = AlignmentMatrix;
				OffsetVectorMag = OffsetVector;
				SensitivityMatrixMag = SensitivityMatrix;

			} else if(packetType==MAG_CALIBRATION_RESPONSE && SensitivityMatrix[0][0]==-1){
				mDefaultCalibrationParametersMag = true;
				if (mShimmerVersion!=3){
					AlignmentMatrixMag = AlignmentMatrixMagShimmer2;
					OffsetVectorMag = OffsetVectorMagShimmer2;
					if (mMagGain==0){
						SensitivityMatrixMag = SensitivityMatrixMag0p8GaShimmer2;
					} else if (mMagGain==1){
						SensitivityMatrixMag = SensitivityMatrixMag1p3GaShimmer2;
					} else if (mMagGain==2){
						SensitivityMatrixMag = SensitivityMatrixMag1p9GaShimmer2;
					} else if (mMagGain==3){
						SensitivityMatrixMag = SensitivityMatrixMag2p5GaShimmer2;
					} else if (mMagGain==4){
						SensitivityMatrixMag = SensitivityMatrixMag4p0GaShimmer2;
					} else if (mMagGain==5){
						SensitivityMatrixMag = SensitivityMatrixMag4p7GaShimmer2;
					} else if (mMagGain==6){
						SensitivityMatrixMag = SensitivityMatrixMag5p6GaShimmer2;
					} else if (mMagGain==7){
						SensitivityMatrixMag = SensitivityMatrixMag8p1GaShimmer2;
					}
				} else {
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
		}
	}

	private double[][] matrixinverse3x3(double[][] data) {
		double a,b,c,d,e,f,g,h,i;
		a=data[0][0];
		b=data[0][1];
		c=data[0][2];
		d=data[1][0];
		e=data[1][1];
		f=data[1][2];
		g=data[2][0];
		h=data[2][1];
		i=data[2][2];
		//
		double deter=a*e*i+b*f*g+c*d*h-c*e*g-b*d*i-a*f*h;
		double[][] answer=new double[3][3];
		answer[0][0]=(1/deter)*(e*i-f*h);

		answer[0][1]=(1/deter)*(c*h-b*i);
		answer[0][2]=(1/deter)*(b*f-c*e);
		answer[1][0]=(1/deter)*(f*g-d*i);
		answer[1][1]=(1/deter)*(a*i-c*g);
		answer[1][2]=(1/deter)*(c*d-a*f);
		answer[2][0]=(1/deter)*(d*h-e*g);
		answer[2][1]=(1/deter)*(g*b-a*h);
		answer[2][2]=(1/deter)*(a*e-b*d);
		return answer;
	}
	private double[][] matrixminus(double[][] a ,double[][] b) {
		int aRows = a.length,
				aColumns = a[0].length,
				bRows = b.length,
				bColumns = b[0].length;
		if (( aColumns != bColumns )&&( aRows != bRows )) {
			throw new IllegalArgumentException(" Matrix did not match");
		}
		double[][] resultant = new double[aRows][bColumns];
		for(int i = 0; i < aRows; i++) { // aRow
			for(int k = 0; k < aColumns; k++) { // aColumn

				resultant[i][k]=a[i][k]-b[i][k];

			}
		}
		return resultant;
	}

	private double[][] matrixmultiplication(double[][] a,double[][] b) {

		int aRows = a.length,
				aColumns = a[0].length,
				bRows = b.length,
				bColumns = b[0].length;

		if ( aColumns != bRows ) {
			throw new IllegalArgumentException("A:Rows: " + aColumns + " did not match B:Columns " + bRows + ".");
		}

		double[][] resultant = new double[aRows][bColumns];

		for(int i = 0; i < aRows; i++) { // aRow
			for(int j = 0; j < bColumns; j++) { // bColumn
				for(int k = 0; k < aColumns; k++) { // aColumn
					resultant[i][j] += a[i][k] * b[k][j];
				}
			}
		}

		return resultant;
	}

	protected double calibrateTimeStamp(double timeStamp){
		//first convert to continuous time stamp
		double calibratedTimeStamp=0;
		if (mLastReceivedTimeStamp>(timeStamp+(65536*mCurrentTimeStampCycle))){ 
			mCurrentTimeStampCycle=mCurrentTimeStampCycle+1;
		}

		mLastReceivedTimeStamp=(timeStamp+(65536*mCurrentTimeStampCycle));
		calibratedTimeStamp=mLastReceivedTimeStamp/32768*1000;   // to convert into mS
		if (mFirstTimeCalTime){
			mFirstTimeCalTime=false;
			mCalTimeStart = calibratedTimeStamp;
		}
		if (mLastReceivedCalibratedTimeStamp!=-1){
			double timeDifference=calibratedTimeStamp-mLastReceivedCalibratedTimeStamp;
			if (timeDifference>(1/(mSamplingRate-1))*1000){
				mPacketLossCount=mPacketLossCount+1;
				Long mTotalNumberofPackets=(long) ((calibratedTimeStamp-mCalTimeStart)/(1/mSamplingRate*1000));

				mPacketReceptionRate = (double)((mTotalNumberofPackets-mPacketLossCount)/(double)mTotalNumberofPackets)*100;
				sendStatusMsgPacketLossDetected();
			}
		}	
		mLastReceivedCalibratedTimeStamp=calibratedTimeStamp;
		return calibratedTimeStamp;
	}

	//protected abstract void sendStatusMsgPacketLossDetected();
	protected void sendStatusMsgPacketLossDetected() {
	}

	protected double[] calibrateInertialSensorData(double[] data, double[][] AM, double[][] SM, double[][] OV) {
		/*  Based on the theory outlined by Ferraris F, Grimaldi U, and Parvis M.  
           in "Procedure for effortless in-field calibration of three-axis rate gyros and accelerometers" Sens. Mater. 1995; 7: 311-30.            
           C = [R^(-1)] .[K^(-1)] .([U]-[B])
			where.....
			[C] -> [3 x n] Calibrated Data Matrix 
			[U] -> [3 x n] Uncalibrated Data Matrix
			[B] ->  [3 x n] Replicated Sensor Offset Vector Matrix 
			[R^(-1)] -> [3 x 3] Inverse Alignment Matrix
			[K^(-1)] -> [3 x 3] Inverse Sensitivity Matrix
			n = Number of Samples
		 */
		double [][] data2d=new double [3][1];
		data2d[0][0]=data[0];
		data2d[1][0]=data[1];
		data2d[2][0]=data[2];
		data2d= matrixmultiplication(matrixmultiplication(matrixinverse3x3(AM),matrixinverse3x3(SM)),matrixminus(data2d,OV));
		double[] ansdata=new double[3];
		ansdata[0]=data2d[0][0];
		ansdata[1]=data2d[1][0];
		ansdata[2]=data2d[2][0];
		return ansdata;
	}

	protected double[] calibratePressureSensorData(double UP, double UT){
		double X1 = (UT - AC6) * AC5 / 32768;
		double X2 = (MC * 2048 / (X1 + MD));
		double B5 = X1 + X2;
		double T = (B5 + 8) / 16;

		double B6 = B5 - 4000;
		X1 = (B2 * (Math.pow(B6,2)/ 4096)) / 2048;
		X2 = AC2 * B6 / 2048;
		double X3 = X1 + X2;
		double B3 = (((AC1 * 4 + X3)*(1<<mPressureResolution) + 2)) / 4;
		X1 = AC3 * B6 / 8192;
		X2 = (B1 * (Math.pow(B6,2)/ 4096)) / 65536;
		X3 = ((X1 + X2) + 2) / 4;
		double B4 = AC4 * (X3 + 32768) / 32768;
		double B7 = (UP - B3) * (50000>>mPressureResolution);
		double p=0;
		if (B7 < 2147483648L ){ //0x80000000
			p = (B7 * 2) / B4;
		}
		else{
			p = (B7 / B4) * 2;
		}
		X1 = ((p / 256.0) * (p / 256.0) * 3038) / 65536;
		X2 = (-7357 * p) / 65536;
		p = p +( (X1 + X2 + 3791) / 16);

		double[] caldata = new double[2];
		caldata[0]=p;
		caldata[1]=T/10;
		return caldata;
	}


	protected double calibrateU12AdcValue(double uncalibratedData,double offset,double vRefP,double gain){
		double calibratedData=(uncalibratedData-offset)*(((vRefP*1000)/gain)/4095);
		return calibratedData;
	}

	protected double calibrateGsrData(double gsrUncalibratedData,double p1, double p2){
		gsrUncalibratedData = (double)((int)gsrUncalibratedData & 4095); 
		//the following polynomial is deprecated and has been replaced with a more accurate linear one, see GSR user guide for further details
		//double gsrCalibratedData = (p1*Math.pow(gsrUncalibratedData,4)+p2*Math.pow(gsrUncalibratedData,3)+p3*Math.pow(gsrUncalibratedData,2)+p4*gsrUncalibratedData+p5)/1000;
		//the following is the new linear method see user GSR user guide for further details
		double gsrCalibratedData = (1/(p1*gsrUncalibratedData+p2))*1000; //kohms 
		return gsrCalibratedData;  
	}

	public double getSamplingRate(){
		return mSamplingRate;
	}

	public int getAccelRange(){
		return mAccelRange;
	}

	public int getPressureResolution(){
		return mPressureResolution;
	}

	public int getMagRange(){
		return mMagGain;
	}

	public int getGyroRange(){
		return mGyroRange;
	}

	public int getGSRRange(){
		return mGSRRange;
	}

	public int getInternalExpPower(){
		return mInternalExpPower;
	}

	public int getPMux(){
		if ((mConfigByte0 & (byte)64)!=0) {
			//then set ConfigByte0 at bit position 7
			return 1;
		} else{
			return 0;
		}
	}

	protected ObjectCluster callAdditionalServices(ObjectCluster objectCluster) {
		// TODO Auto-generated method stub
		return objectCluster;
	}

	protected void interpretInqResponse(byte[] bufferInquiry){
		if (mShimmerVersion==SHIMMER_2 || mShimmerVersion==SHIMMER_2R){

			mPacketSize = 2+bufferInquiry[3]*2; 
			mSamplingRate = (double)1024/bufferInquiry[0];
			if (mMagSamplingRate==3 && mSamplingRate>10){
				mLowPowerMag = true;
			}
			mAccelRange = bufferInquiry[1];
			mConfigByte0 = bufferInquiry[2] & 0xFF; //convert the byte to unsigned integer
			mNChannels = bufferInquiry[3];
			mBufferSize = bufferInquiry[4];
			byte[] signalIdArray = new byte[mNChannels];
			System.arraycopy(bufferInquiry, 5, signalIdArray, 0, mNChannels);
			updateEnabledSensorsFromChannels(signalIdArray);
			interpretdatapacketformat(mNChannels,signalIdArray);
			mInquiryResponseBytes = new byte[5+mNChannels];
			System.arraycopy(bufferInquiry, 0, mInquiryResponseBytes , 0, mInquiryResponseBytes.length);
		} else if (mShimmerVersion==SHIMMER_3) {
			mPacketSize = 2+bufferInquiry[6]*2; 
			mSamplingRate = (32768/(double)((int)(bufferInquiry[0] & 0xFF) + ((int)(bufferInquiry[1] & 0xFF) << 8)));
			mNChannels = bufferInquiry[6];
			mBufferSize = bufferInquiry[7];
			mConfigByte0 = ((long)(bufferInquiry[2] & 0xFF) +((long)(bufferInquiry[3] & 0xFF) << 8)+((long)(bufferInquiry[4] & 0xFF) << 16) +((long)(bufferInquiry[5] & 0xFF) << 24));
			mAccelRange = ((int)(mConfigByte0 & 0xC))>>2;
			mGyroRange = ((int)(mConfigByte0 & 196608))>>16;
			mMagGain = ((int)(mConfigByte0 & 14680064))>>21;
			mAccelSamplingRate = ((int)(mConfigByte0 & 0xF0))>>4;
			mMPU9150SamplingRate = ((int)(mConfigByte0 & 65280))>>8;
		mMagSamplingRate = ((int)(mConfigByte0 & 1835008))>>18; 
		mPressureResolution = (((int)(mConfigByte0 >>28)) & 3);
		mGSRRange  = (((int)(mConfigByte0 >>25)) & 7);
		mInternalExpPower = (((int)(mConfigByte0 >>24)) & 1);
		mInquiryResponseBytes = new byte[8+mNChannels];
		System.arraycopy(bufferInquiry, 0, mInquiryResponseBytes , 0, mInquiryResponseBytes.length);
		if ((mAccelSamplingRate==2 && mSamplingRate>10)){
			mLowPowerAccel = true;
		}
		if ((mMPU9150SamplingRate==0xFF && mSamplingRate>10)){
			mLowPowerGyro = true;
		}
		if ((mMagSamplingRate==4 && mSamplingRate>10)){
			mLowPowerMag = true;
		}
		byte[] signalIdArray = new byte[mNChannels];
		System.arraycopy(bufferInquiry, 8, signalIdArray, 0, mNChannels);
		updateEnabledSensorsFromChannels(signalIdArray);
		interpretdatapacketformat(mNChannels,signalIdArray);
		} else if (mShimmerVersion==SHIMMER_SR30) {
			mPacketSize = 2+bufferInquiry[2]*2; 
			mSamplingRate = (double)1024/bufferInquiry[0];
			mAccelRange = bufferInquiry[1];
			mNChannels = bufferInquiry[2];
			mBufferSize = bufferInquiry[3];
			byte[] signalIdArray = new byte[mNChannels];
			System.arraycopy(bufferInquiry, 4, signalIdArray, 0, mNChannels); // this is 4 because there is no config byte
			interpretdatapacketformat(mNChannels,signalIdArray);

		}
	}

	protected void updateEnabledSensorsFromChannels(byte[] channels){
		// set the sensors value
		// crude way of getting this value, but allows for more customised firmware
		// to still work with this application
		// e.g. if any axis of the accelerometer is being transmitted, then it will
		// recognise that the accelerometer is being sampled
		int enabledSensors = 0;
		for (int i=0;i<channels.length;i++)
		{
			if (mShimmerVersion==SHIMMER_3){
				if (channels[i]==Configuration.Shimmer3.Channel.XAAccel || channels[i]==Configuration.Shimmer3.Channel.YAAccel || channels[i]==Configuration.Shimmer3.Channel.ZAAccel){
					enabledSensors = enabledSensors | SENSOR_ACCEL;
				}
				if (channels[i]==Configuration.Shimmer3.Channel.XDAccel || channels[i]==Configuration.Shimmer3.Channel.YDAccel||channels[i]==Configuration.Shimmer3.Channel.ZDAccel){
					enabledSensors = enabledSensors | SENSOR_DACCEL;
				}
				if (channels[i]==Configuration.Shimmer3.Channel.XGyro || channels[i]==Configuration.Shimmer3.Channel.YGyro||channels[i]==Configuration.Shimmer3.Channel.ZGyro){
					enabledSensors = enabledSensors | SENSOR_GYRO;
				}
				if (channels[i]==Configuration.Shimmer3.Channel.XMag || channels[i]==Configuration.Shimmer3.Channel.YMag||channels[i]==Configuration.Shimmer3.Channel.ZMag){
					enabledSensors = enabledSensors | SENSOR_MAG;
				}
				if (channels[i]==Configuration.Shimmer3.Channel.VBatt){
					enabledSensors = enabledSensors | SENSOR_BATT;
				}
				if (channels[i]==Configuration.Shimmer3.Channel.ExtAdc7){
					enabledSensors = enabledSensors | SENSOR_EXT_ADC_A7;
				}
				if (channels[i]==Configuration.Shimmer3.Channel.ExtAdc6){
					enabledSensors = enabledSensors | SENSOR_EXT_ADC_A6;
				}
				if (channels[i]==Configuration.Shimmer3.Channel.ExtAdc15){
					enabledSensors = enabledSensors | SENSOR_EXT_ADC_A15;
				}
				if (channels[i]==Configuration.Shimmer3.Channel.IntAdc1){
					enabledSensors = enabledSensors | SENSOR_INT_ADC_A1;
				}
				if (channels[i]==Configuration.Shimmer3.Channel.IntAdc12){
					enabledSensors = enabledSensors | SENSOR_INT_ADC_A12;
				}
				if (channels[i]==Configuration.Shimmer3.Channel.IntAdc13){
					enabledSensors = enabledSensors | SENSOR_INT_ADC_A13;
				}
				if (channels[i]==Configuration.Shimmer3.Channel.IntAdc14){
					enabledSensors = enabledSensors | SENSOR_INT_ADC_A14;
				}
				if (channels[i]==Configuration.Shimmer3.Channel.Pressure){
					enabledSensors = enabledSensors | SENSOR_BMP180;
				}
				if (channels[i]==Configuration.Shimmer3.Channel.Temperature){
					enabledSensors = enabledSensors | SENSOR_BMP180;
				}
				if (channels[i]==Configuration.Shimmer3.Channel.GsrRaw){
					enabledSensors = enabledSensors | SENSOR_GSR;
				}
				if (channels[i]==Configuration.Shimmer3.Channel.EXG_ADS1292R_1_STATUS){
					//enabledSensors = enabledSensors | SENSOR_EXG1_24BIT;
				}
				if (channels[i]==Configuration.Shimmer3.Channel.EXG_ADS1292R_1_CH1_24BIT){
					enabledSensors = enabledSensors | SENSOR_EXG1_24BIT;
				}
				if (channels[i]==Configuration.Shimmer3.Channel.EXG_ADS1292R_1_CH2_24BIT){
					enabledSensors = enabledSensors | SENSOR_EXG1_24BIT;
				}
				if (channels[i]==Configuration.Shimmer3.Channel.EXG_ADS1292R_1_CH1_16BIT){
					enabledSensors = enabledSensors | SENSOR_EXG1_16BIT;
				}
				if (channels[i]==Configuration.Shimmer3.Channel.EXG_ADS1292R_1_CH2_16BIT){
					enabledSensors = enabledSensors | SENSOR_EXG1_16BIT;
				}
				if (channels[i]==Configuration.Shimmer3.Channel.EXG_ADS1292R_2_STATUS){
					//enabledSensors = enabledSensors | SENSOR_EXG2_24BIT;
				}
				if (channels[i]==Configuration.Shimmer3.Channel.EXG_ADS1292R_2_CH1_24BIT){
					enabledSensors = enabledSensors | SENSOR_EXG2_24BIT;
				}
				if (channels[i]==Configuration.Shimmer3.Channel.EXG_ADS1292R_2_CH2_24BIT){
					enabledSensors = enabledSensors | SENSOR_EXG2_24BIT;
				}
				if (channels[i]==Configuration.Shimmer3.Channel.EXG_ADS1292R_2_CH1_16BIT){
					enabledSensors = enabledSensors | SENSOR_EXG2_16BIT;
				}
				if (channels[i]==Configuration.Shimmer3.Channel.EXG_ADS1292R_2_CH2_16BIT){
					enabledSensors = enabledSensors | SENSOR_EXG2_16BIT;
				}
				if ((channels[i] == Configuration.Shimmer3.Channel.BridgeAmpHigh) || (channels[i] == Configuration.Shimmer3.Channel.BridgeAmpLow))
				{
					enabledSensors = enabledSensors | SENSOR_BRIDGE_AMP;
				}

			} else if(mShimmerVersion==SHIMMER_2R){
				if (channels[i]==Configuration.Shimmer2.Channel.XAccel || channels[i]==Configuration.Shimmer2.Channel.YAccel||channels[i]==Configuration.Shimmer2.Channel.ZAccel){
					enabledSensors = enabledSensors | SENSOR_ACCEL;
				}
				if (channels[i]==Configuration.Shimmer2.Channel.XGyro || channels[i]==Configuration.Shimmer2.Channel.YGyro ||channels[i]==Configuration.Shimmer2.Channel.ZGyro){
					enabledSensors = enabledSensors | SENSOR_GYRO;
				}
				if (channels[i]==Configuration.Shimmer2.Channel.XMag || channels[i]==Configuration.Shimmer2.Channel.XMag ||channels[i]==Configuration.Shimmer2.Channel.XMag){
					enabledSensors = enabledSensors | SENSOR_MAG;
				}        	
				if ((channels[i] == Configuration.Shimmer2.Channel.EcgLaLl) || (channels[i] == Configuration.Shimmer2.Channel.EcgRaLl))
				{
					enabledSensors = enabledSensors | SENSOR_ECG;
				}
				else if (channels[i] == Configuration.Shimmer2.Channel.Emg)
				{
					enabledSensors = enabledSensors | SENSOR_EMG;
				}
				else if (channels[i] == Configuration.Shimmer2.Channel.AnExA0 && getPMux()==0)
				{
					enabledSensors = enabledSensors | SENSOR_EXP_BOARD_A0;
				}
				else if (channels[i] == Configuration.Shimmer2.Channel.AnExA7 && getPMux()==0)
				{
					enabledSensors = enabledSensors | SENSOR_EXP_BOARD_A7;
				}
				else if ((channels[i] == Configuration.Shimmer2.Channel.BridgeAmpHigh) || (channels[i] == Configuration.Shimmer2.Channel.BridgeAmpLow))
				{
					enabledSensors = enabledSensors | SENSOR_BRIDGE_AMP;
				}
				else if ((channels[i] == Configuration.Shimmer2.Channel.GsrRaw) || (channels[i] == Configuration.Shimmer2.Channel.GsrRes))
				{
					enabledSensors = enabledSensors | SENSOR_GSR;
				}
				else if (channels[i] == Configuration.Shimmer2.Channel.HeartRate)
				{
					enabledSensors = enabledSensors | SENSOR_HEART;
				}   else if (channels[i] == Configuration.Shimmer2.Channel.AnExA0 && getPMux()==1)
				{
					enabledSensors = enabledSensors | SENSOR_BATT;
				}
				else if (channels[i] == Configuration.Shimmer2.Channel.AnExA7 && getPMux()==1)
				{
					enabledSensors = enabledSensors | SENSOR_BATT;
				}
			} 
		}
		mEnabledSensors=enabledSensors;	
	}
	public String getDeviceName(){
		return mMyName;
	}
	public String getBluetoothAddress(){
		return  mMyBluetoothAddress;
	}
	public void setDeviceName(String deviceName) {
		// TODO Auto-generated method stub
		mMyName = deviceName;
	}
	public byte[] getRawInquiryResponse(){
		return mInquiryResponseBytes;
	}

	public byte[] getRawCalibrationParameters(){

		byte[] rawcal=new byte[1];
		if (mShimmerVersion==SHIMMER_3)
		{
			//Accel + Digi Accel + Gyro + Mag + Pressure
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream( );
			try {
				outputStream.write(5); // write the number of different calibration parameters
				outputStream.write( mAccelCalRawParams.length);
				outputStream.write( mAccelCalRawParams);
				outputStream.write( mDigiAccelCalRawParams.length);
				outputStream.write( mDigiAccelCalRawParams );
				outputStream.write( mGyroCalRawParams.length );
				outputStream.write( mGyroCalRawParams );
				outputStream.write( mMagCalRawParams.length );
				outputStream.write( mMagCalRawParams );
				outputStream.write( mPressureCalRawParams.length);
				outputStream.write( mPressureCalRawParams );
				rawcal = outputStream.toByteArray( );
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}			

		} else if (mShimmerVersion==SHIMMER_2 ||mShimmerVersion==SHIMMER_2R)
		{
			//Accel + Digi Accel + Gyro + Mag + Pressure
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream( );
			try 
			{
				outputStream.write(5); // write the number of different calibration parameters
				outputStream.write( mAccelCalRawParams.length);
				outputStream.write( mAccelCalRawParams);
				outputStream.write( mGyroCalRawParams.length );
				outputStream.write( mGyroCalRawParams );
				outputStream.write( mMagCalRawParams.length );
				outputStream.write( mMagCalRawParams );
				outputStream.write( mECGCalRawParams.length );
				outputStream.write( mECGCalRawParams );
				outputStream.write( mEMGCalRawParams.length );
				outputStream.write( mEMGCalRawParams );
				rawcal = outputStream.toByteArray( );
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}		

		} else {
			rawcal[0]=0;
		}
		return rawcal;

	}



	public List<String> getListofEnabledSensors(){
		List<String> listofSensors = new ArrayList<String>();
		if (mShimmerVersion==SHIMMER_3){
			if (((mEnabledSensors & 0xFF)& SENSOR_ACCEL) > 0){
				listofSensors.add("Low Noise Accelerometer");
			}
			if ((mEnabledSensors & SENSOR_DACCEL) > 0){
				listofSensors.add("Wide Range Accelerometer");
			}
		} else {
			if (((mEnabledSensors & 0xFF)& SENSOR_ACCEL) > 0){
				listofSensors.add("Accelerometer");
			}
		}
		if (((mEnabledSensors & 0xFF)& SENSOR_GYRO) > 0) {
			listofSensors.add("Gyroscope");
		}
		if (((mEnabledSensors & 0xFF)& SENSOR_MAG) > 0) {
			listofSensors.add("Magnetometer");
		}
		if (((mEnabledSensors & 0xFF) & SENSOR_GSR) > 0) {
			listofSensors.add("GSR");
		}
		if (((mEnabledSensors & 0xFF) & SENSOR_ECG) > 0) {
			listofSensors.add("ECG");
		}
		if (((mEnabledSensors & 0xFF) & SENSOR_EMG) > 0) {
			listofSensors.add("EMG");
		}
		if (((mEnabledSensors & 0xFF00) & SENSOR_BRIDGE_AMP) > 0) {
			listofSensors.add("Bridge Amplifier");
		}
		if (((mEnabledSensors & 0xFF00) & SENSOR_HEART) > 0) {
			listofSensors.add("Heart Rate");
		}
		if (((mEnabledSensors & 0xFF) & SENSOR_EXP_BOARD_A0) > 0 && (mEnabledSensors & SENSOR_BATT) == 0 && mShimmerVersion != SHIMMER_3) {
			listofSensors.add("ExpBoard A0");
		}
		if (((mEnabledSensors & 0xFF) & SENSOR_EXP_BOARD_A7) > 0  && (mEnabledSensors & SENSOR_BATT) == 0 && mShimmerVersion != SHIMMER_3) {
			listofSensors.add("ExpBoard A7");
		}
		if ((mEnabledSensors & SENSOR_BATT) > 0) {
			listofSensors.add("Battery Voltage");
		}
		if (((mEnabledSensors & 0xFF) & SENSOR_EXT_ADC_A7) > 0  && mShimmerVersion == SHIMMER_3) {
			listofSensors.add("External ADC A7");
		}
		if (((mEnabledSensors & 0xFF) & SENSOR_EXT_ADC_A6) > 0  && mShimmerVersion == SHIMMER_3) {
			listofSensors.add("External ADC A6");
		}
		if (((mEnabledSensors & 0xFFFF) & SENSOR_EXT_ADC_A15) > 0  && mShimmerVersion == SHIMMER_3) {
			listofSensors.add("External ADC A15");
		}
		if (((mEnabledSensors & 0xFFFF) & SENSOR_INT_ADC_A1) > 0  && mShimmerVersion == SHIMMER_3) {
			listofSensors.add("Internal ADC A1");
		}
		if (((mEnabledSensors & 0xFFFF) & SENSOR_INT_ADC_A12) > 0  && mShimmerVersion == SHIMMER_3) {
			listofSensors.add("Internal ADC A12");
		}
		if (((mEnabledSensors & 0xFFFFFF) & SENSOR_INT_ADC_A13) > 0  && mShimmerVersion == SHIMMER_3) {
			listofSensors.add("Internal ADC A13");
		}
		if (((mEnabledSensors & 0xFFFFFF) & SENSOR_INT_ADC_A14) > 0  && mShimmerVersion == SHIMMER_3) {
			listofSensors.add("Internal ADC A14");
		}
		if ((mEnabledSensors & SENSOR_BMP180) > 0  && mShimmerVersion == SHIMMER_3) {
			listofSensors.add("Pressure");
		}
		if ((mEnabledSensors & SENSOR_EXG1_24BIT) > 0  && mShimmerVersion == SHIMMER_3) {
			listofSensors.add("EXG1");
		}
		if ((mEnabledSensors & SENSOR_EXG2_24BIT) > 0  && mShimmerVersion == SHIMMER_3) {
			listofSensors.add("EXG2");
		}
		if ((mEnabledSensors & SENSOR_EXG1_16BIT) > 0  && mShimmerVersion == SHIMMER_3) {
			listofSensors.add("EXG1 16Bit");
		}
		if ((mEnabledSensors & SENSOR_EXG2_16BIT) > 0  && mShimmerVersion == SHIMMER_3) {
			listofSensors.add("EXG2 16Bit");
		}

		return listofSensors;
	}
	
	/** Returns a list of string[] of the four properties. 1) Shimmer Name - 2) Property/Signal Name - 3) Format Name - 4) Unit Name
	 * @return list string array of properties
	 */
	public List<String[]> getListofEnabledSensorSignalsandFormats(){
		List<String[]> listofSignals = new ArrayList<String[]>();
		 
		if (mShimmerVersion!=SHIMMER_3){
			String[] channel = new String[]{mMyName,"Timestamp","CAL","mSecs"};
			listofSignals.add(channel);
			channel = new String[]{mMyName,"Timestamp","RAW","no units"};
			listofSignals.add(channel);
			if (((mEnabledSensors & 0xFF)& SENSOR_ACCEL) > 0){
				String unit = "m/(sec^2)";
				if (mDefaultCalibrationParametersAccel == true) {
					unit = "m/(sec^2)*";
				}
				
				channel = new String[]{mMyName,"Accelerometer X","CAL",unit};
				listofSignals.add(channel);
				channel = new String[]{mMyName,"Accelerometer X","RAW","no units"};
				listofSignals.add(channel);
				
				channel = new String[]{mMyName,"Accelerometer Y","CAL",unit};
				listofSignals.add(channel);
				channel = new String[]{mMyName,"Accelerometer Y","RAW","no units"};
				listofSignals.add(channel);
				
				channel = new String[]{mMyName,"Accelerometer Z","CAL",unit};
				listofSignals.add(channel);
				channel = new String[]{mMyName,"Accelerometer Z","RAW","no units"};
				listofSignals.add(channel);
				
			}
			if (((mEnabledSensors & 0xFF)& SENSOR_GYRO) > 0) {
				String unit = "deg/sec";
				if (mDefaultCalibrationParametersGyro == true) {
					unit = "deg/sec*";
				} 
				channel = new String[]{mMyName,"Gyroscope X","CAL",unit};
				listofSignals.add(channel);
				channel = new String[]{mMyName,"Gyroscope X","RAW","no units"};
				listofSignals.add(channel);
				
				channel = new String[]{mMyName,"Gyroscope Y","CAL",unit};
				listofSignals.add(channel);
				channel = new String[]{mMyName,"Gyroscope Y","RAW","no units"};
				listofSignals.add(channel);
				
				channel = new String[]{mMyName,"Gyroscope Z","CAL",unit};
				listofSignals.add(channel);
				channel = new String[]{mMyName,"Gyroscope Z","RAW","no units"};
				listofSignals.add(channel);
			}
			if (((mEnabledSensors & 0xFF)& SENSOR_MAG) > 0) {
				String unit = "local";
				if (mDefaultCalibrationParametersGyro == true) {
					unit = "local*";
				} 
				channel = new String[]{mMyName,"Magnetometer X","CAL",unit};
				listofSignals.add(channel);
				channel = new String[]{mMyName,"Magnetometer X","RAW","no units"};
				listofSignals.add(channel);
				
				channel = new String[]{mMyName,"Magnetometer Y","CAL",unit};
				listofSignals.add(channel);
				channel = new String[]{mMyName,"Magnetometer Y","RAW","no units"};
				listofSignals.add(channel);
				
				channel = new String[]{mMyName,"Magnetometer Z","CAL",unit};
				listofSignals.add(channel);
				channel = new String[]{mMyName,"Magnetometer Z","RAW","no units"};
				listofSignals.add(channel);
			}
			if (((mEnabledSensors & 0xFF) & SENSOR_GSR) > 0) {
				channel = new String[]{mMyName,"GSR","CAL","kOhms"};
				listofSignals.add(channel);
				channel = new String[]{mMyName,"Magnetometer X","RAW","no units"};
				listofSignals.add(channel);
			}
			if (((mEnabledSensors & 0xFF) & SENSOR_ECG) > 0) {
				
				String unit = "mVolts";
				if (mDefaultCalibrationParametersECG == true) {
					unit = "mVolts*";
				}
				
				channel = new String[]{mMyName,"ECG RA-LL","CAL",unit};
				listofSignals.add(channel);
				channel = new String[]{mMyName,"ECG RA-LL","RAW","no units"};
				listofSignals.add(channel);
				
				channel = new String[]{mMyName,"ECG LA-LL","CAL",unit};
				listofSignals.add(channel);
				channel = new String[]{mMyName,"ECG LA-LL","RAW","no units"};
				listofSignals.add(channel);
				
			}
			if (((mEnabledSensors & 0xFF) & SENSOR_EMG) > 0) {
				String unit = "mVolts";
				if (mDefaultCalibrationParametersECG == true) {
					unit = "mVolts*";
				}
				
				channel = new String[]{mMyName,"EMG","CAL",unit};
				listofSignals.add(channel);
				channel = new String[]{mMyName,"EMG","RAW","no units"};
				listofSignals.add(channel);
			}
			if (((mEnabledSensors & 0xFF00) & SENSOR_BRIDGE_AMP) > 0) {
				String unit = "mVolts";
				channel = new String[]{mMyName,"Bridge Amplifier High","CAL",unit};
				listofSignals.add(channel);
				channel = new String[]{mMyName,"Bridge Amplifier High","RAW","no units"};
				listofSignals.add(channel);
				
				channel = new String[]{mMyName,"Bridge Amplifier Low","CAL",unit};
				listofSignals.add(channel);
				channel = new String[]{mMyName,"Bridge Amplifier Low","RAW","no units"};
				listofSignals.add(channel);
			}
			if (((mEnabledSensors & 0xFF00) & SENSOR_HEART) > 0) {
				String unit = "BPM";
				channel = new String[]{mMyName,"Heart Rate","CAL",unit};
				listofSignals.add(channel);
				channel = new String[]{mMyName,"Heart Rate","RAW","no units"};
				listofSignals.add(channel);
			}
			if ((((mEnabledSensors & 0xFF) & SENSOR_EXP_BOARD_A0) > 0) && getPMux() == 0) {
				String unit = "mVolts";
				channel = new String[]{mMyName,"ExpBoard A0","CAL",unit};
				listofSignals.add(channel);
				channel = new String[]{mMyName,"ExpBoard A0","RAW","no units"};
				listofSignals.add(channel);
			}
			if ((((mEnabledSensors & 0xFF) & SENSOR_EXP_BOARD_A7) > 0 && getPMux() == 0)) {
				String unit = "mVolts";
				channel = new String[]{mMyName,"ExpBoard A7","CAL",unit};
				listofSignals.add(channel);
				channel = new String[]{mMyName,"ExpBoard A7","RAW","no units"};
				listofSignals.add(channel);
			}
			if (((mEnabledSensors & 0xFFFF) & SENSOR_BATT) > 0) {
				String unit = "mVolts";
				channel = new String[]{mMyName,"VSenseReg","CAL",unit};
				listofSignals.add(channel);
				channel = new String[]{mMyName,"VSenseReg","RAW","no units"};
				listofSignals.add(channel);
				channel = new String[]{mMyName,"VSenseBatt","CAL",unit};
				listofSignals.add(channel);
				channel = new String[]{mMyName,"VSenseBatt","RAW","no units"};
				listofSignals.add(channel);
			}
			if (((mEnabledSensors & 0xFF)& SENSOR_ACCEL) > 0 && ((mEnabledSensors & 0xFF)& SENSOR_GYRO) > 0 && ((mEnabledSensors & 0xFF)& SENSOR_MAG) > 0 && mOrientationEnabled){
				String unit = "local";
				channel = new String[]{mMyName,"Axis Angle A","CAL",unit};
				listofSignals.add(channel);
				channel = new String[]{mMyName,"Axis Angle X","CAL",unit};
				listofSignals.add(channel);
				channel = new String[]{mMyName,"Axis Angle Y","CAL",unit};
				listofSignals.add(channel);
				channel = new String[]{mMyName,"Axis Angle Z","CAL",unit};
				listofSignals.add(channel);
				channel = new String[]{mMyName,"Quaternion 0","CAL",unit};
				listofSignals.add(channel);
				channel = new String[]{mMyName,"Quaternion 1","CAL",unit};
				listofSignals.add(channel);
				channel = new String[]{mMyName,"Quaternion 2","CAL",unit};
				listofSignals.add(channel);
				channel = new String[]{mMyName,"Quaternion 3","CAL",unit};
				listofSignals.add(channel);
			}
//			if (((mEnabledSensors & 0xFF)& SENSOR_ACCEL) > 0 && ((mEnabledSensors & 0xFF)& SENSOR_GYRO) > 0 && ((mEnabledSensors & 0xFF)& SENSOR_MAG) > 0 && mOrientationEnabled){
//				String unit = "local";
//				channel = new String[]{mMyName,"Quaternion 0","CAL",unit};
//				listofSignals.add(channel);
//				channel = new String[]{mMyName,"Quaternion 1","CAL",unit};
//				listofSignals.add(channel);
//				channel = new String[]{mMyName,"Quaternion 2","CAL",unit};
//				listofSignals.add(channel);
//				channel = new String[]{mMyName,"Quaternion 3","CAL",unit};
//				listofSignals.add(channel);
//			}

		} else {

			String[] channel = new String[]{mMyName,"Timestamp","CAL","mSecs"};
			listofSignals.add(channel);
			channel = new String[]{mMyName,"Timestamp","RAW","no units"};
			listofSignals.add(channel);
			if ((mEnabledSensors & SENSOR_ACCEL) >0){

				String unit = "m/(sec^2)";
				if (mDefaultCalibrationParametersAccel == true) {
					unit = "m/(sec^2)*";
				}
				
				channel = new String[]{mMyName,"Low Noise Accelerometer X","CAL",unit};
				listofSignals.add(channel);
				channel = new String[]{mMyName,"Low Noise Accelerometer X","RAW","no units"};
				listofSignals.add(channel);
				
				channel = new String[]{mMyName,"Low Noise Accelerometer Y","CAL",unit};
				listofSignals.add(channel);
				channel = new String[]{mMyName,"Low Noise Accelerometer Y","RAW","no units"};
				listofSignals.add(channel);
				
				channel = new String[]{mMyName,"Low Noise Accelerometer Z","CAL",unit};
				listofSignals.add(channel);
				channel = new String[]{mMyName,"Low Noise Accelerometer Z","RAW","no units"};
				listofSignals.add(channel);
				
			}
			if ((mEnabledSensors& SENSOR_DACCEL) >0){


				String unit = "m/(sec^2)";
				if (mDefaultCalibrationParametersDigitalAccel == true) {
					unit = "m/(sec^2)*";
				}
				
				channel = new String[]{mMyName,"Wide Range Accelerometer X","CAL",unit};
				listofSignals.add(channel);
				channel = new String[]{mMyName,"Wide Range Accelerometer X","RAW","no units"};
				listofSignals.add(channel);
				
				channel = new String[]{mMyName,"Wide Range Accelerometer Y","CAL",unit};
				listofSignals.add(channel);
				channel = new String[]{mMyName,"Wide Range Accelerometer Y","RAW","no units"};
				listofSignals.add(channel);
				
				channel = new String[]{mMyName,"Wide Range Accelerometer Z","CAL",unit};
				listofSignals.add(channel);
				channel = new String[]{mMyName,"Wide Range Accelerometer Z","RAW","no units"};
				listofSignals.add(channel);
				
			
			}
			if (((mEnabledSensors & 0xFF)& SENSOR_GYRO) > 0) {
				String unit = "deg/sec";
				if (mDefaultCalibrationParametersGyro == true) {
					unit = "deg/sec*";
				} 
				channel = new String[]{mMyName,"Gyroscope X","CAL",unit};
				listofSignals.add(channel);
				channel = new String[]{mMyName,"Gyroscope X","RAW","no units"};
				listofSignals.add(channel);
				
				channel = new String[]{mMyName,"Gyroscope Y","CAL",unit};
				listofSignals.add(channel);
				channel = new String[]{mMyName,"Gyroscope Y","RAW","no units"};
				listofSignals.add(channel);
				
				channel = new String[]{mMyName,"Gyroscope Z","CAL",unit};
				listofSignals.add(channel);
				channel = new String[]{mMyName,"Gyroscope Z","RAW","no units"};
				listofSignals.add(channel);
			}
			if (((mEnabledSensors & 0xFF)& SENSOR_MAG) > 0) {
				String unit = "local";
				if (mDefaultCalibrationParametersGyro == true) {
					unit = "local*";
				} 
				channel = new String[]{mMyName,"Magnetometer X","CAL",unit};
				listofSignals.add(channel);
				channel = new String[]{mMyName,"Magnetometer X","RAW","no units"};
				listofSignals.add(channel);
				
				channel = new String[]{mMyName,"Magnetometer Y","CAL",unit};
				listofSignals.add(channel);
				channel = new String[]{mMyName,"Magnetometer Y","RAW","no units"};
				listofSignals.add(channel);
				
				channel = new String[]{mMyName,"Magnetometer Z","CAL",unit};
				listofSignals.add(channel);
				channel = new String[]{mMyName,"Magnetometer Z","RAW","no units"};
				listofSignals.add(channel);
			} 
			if (((mEnabledSensors & 0xFFFF) & SENSOR_BATT) > 0) {
				String unit = "mVolts";
				channel = new String[]{mMyName,"VSenseBatt","CAL",unit};
				listofSignals.add(channel);
				channel = new String[]{mMyName,"VSenseBatt","RAW","no units"};
				listofSignals.add(channel);
				
			}
			if (((mEnabledSensors & 0xFFFFFF)& SENSOR_EXT_ADC_A15) > 0) {
				String unit = "mVolts";
				channel = new String[]{mMyName,"External ADC A15","CAL",unit};
				listofSignals.add(channel);
				channel = new String[]{mMyName,"External ADC A15","RAW","no units"};
				listofSignals.add(channel);
				
			}
			if (((mEnabledSensors & 0xFFFFFF)& SENSOR_EXT_ADC_A7) > 0) {
				String unit = "mVolts";
				channel = new String[]{mMyName,"External ADC A7","CAL",unit};
				listofSignals.add(channel);
				channel = new String[]{mMyName,"External ADC A7","RAW","no units"};
				listofSignals.add(channel);
			}
			if (((mEnabledSensors & 0xFFFFFF)& SENSOR_EXT_ADC_A6) > 0) {
				String unit = "mVolts";
				channel = new String[]{mMyName,"External ADC A6","CAL",unit};
				listofSignals.add(channel);
				channel = new String[]{mMyName,"External ADC A6","RAW","no units"};
				listofSignals.add(channel);
			}
			if (((mEnabledSensors & 0xFFFFFF)& SENSOR_INT_ADC_A1) > 0) {
				String unit = "mVolts";
				channel = new String[]{mMyName,"Internal ADC A1","CAL",unit};
				listofSignals.add(channel);
				channel = new String[]{mMyName,"Internal ADC A1","RAW","no units"};
				listofSignals.add(channel);
			}
			if (((mEnabledSensors & 0xFFFFFF)& SENSOR_INT_ADC_A12) > 0) {
				String unit = "mVolts";
				channel = new String[]{mMyName,"Internal ADC A12","CAL",unit};
				listofSignals.add(channel);
				channel = new String[]{mMyName,"Internal ADC A12","RAW","no units"};
				listofSignals.add(channel);
			}
			if (((mEnabledSensors & 0xFFFFFF)& SENSOR_INT_ADC_A13) > 0) {
				String unit = "mVolts";
				channel = new String[]{mMyName,"Internal ADC A13","CAL",unit};
				listofSignals.add(channel);
				channel = new String[]{mMyName,"Internal ADC A13","RAW","no units"};
				listofSignals.add(channel);
			}
			if (((mEnabledSensors & 0xFFFFFF)& SENSOR_INT_ADC_A14) > 0) {
				String unit = "mVolts";
				channel = new String[]{mMyName,"Internal ADC A14","CAL",unit};
				listofSignals.add(channel);
				channel = new String[]{mMyName,"Internal ADC A14","RAW","no units"};
				listofSignals.add(channel);
			}
			if ((((mEnabledSensors & 0xFF)& SENSOR_ACCEL) > 0 || ((mEnabledSensors & 0xFFFF)& SENSOR_DACCEL) > 0)&& ((mEnabledSensors & 0xFF)& SENSOR_GYRO) > 0 && ((mEnabledSensors & 0xFF)& SENSOR_MAG) > 0 && mOrientationEnabled){
				String unit = "local";
				channel = new String[]{mMyName,"Axis Angle A","CAL",unit};
				listofSignals.add(channel);
				channel = new String[]{mMyName,"Axis Angle X","CAL",unit};
				listofSignals.add(channel);
				channel = new String[]{mMyName,"Axis Angle Y","CAL",unit};
				listofSignals.add(channel);
				channel = new String[]{mMyName,"Axis Angle Z","CAL",unit};
				listofSignals.add(channel);
				channel = new String[]{mMyName,"Quaternion 0","CAL",unit};
				listofSignals.add(channel);
				channel = new String[]{mMyName,"Quaternion 1","CAL",unit};
				listofSignals.add(channel);
				channel = new String[]{mMyName,"Quaternion 2","CAL",unit};
				listofSignals.add(channel);
				channel = new String[]{mMyName,"Quaternion 3","CAL",unit};
				listofSignals.add(channel);
			}
//			if ((((mEnabledSensors & 0xFF)& SENSOR_ACCEL) > 0 || ((mEnabledSensors & 0xFFFF)& SENSOR_DACCEL) > 0) && ((mEnabledSensors & 0xFF)& SENSOR_GYRO) > 0 && ((mEnabledSensors & 0xFF)& SENSOR_MAG) > 0 && mOrientationEnabled){
//				String unit = "local";
//				channel = new String[]{mMyName,"Quaternion 0","CAL",unit};
//				listofSignals.add(channel);
//				channel = new String[]{mMyName,"Quaternion 1","CAL",unit};
//				listofSignals.add(channel);
//				channel = new String[]{mMyName,"Quaternion 2","CAL",unit};
//				listofSignals.add(channel);
//				channel = new String[]{mMyName,"Quaternion 3","CAL",unit};
//				listofSignals.add(channel);
//			}
			if ((mEnabledSensors & SENSOR_BMP180)>0){
				channel = new String[]{mMyName,"Pressure","CAL","kPa"};
				listofSignals.add(channel);
				channel = new String[]{mMyName,"Pressure","RAW","no units"};
				listofSignals.add(channel);
				channel = new String[]{mMyName,"Temperature","CAL","Celsius"};
				listofSignals.add(channel);
				channel = new String[]{mMyName,"Temperature","RAW","no units"};
				listofSignals.add(channel);
			}
			if ((mEnabledSensors & SENSOR_GSR)>0){
				channel = new String[]{mMyName,"GSR","CAL","kOhms"};
				listofSignals.add(channel);
				channel = new String[]{mMyName,"Magnetometer X","RAW","no units"};
				listofSignals.add(channel);
			}
			if (((mEnabledSensors & SENSOR_EXG1_24BIT)>0)|| ((mEnabledSensors & SENSOR_EXG1_16BIT)>0)){
				channel = new String[]{mMyName,"EXG1 STATUS","RAW","no units"};
				listofSignals.add(channel);
			}
			if (((mEnabledSensors & SENSOR_EXG2_24BIT)>0)|| ((mEnabledSensors & SENSOR_EXG2_16BIT)>0)){
				channel = new String[]{mMyName,"EXG2 STATUS","RAW","no units"};
				listofSignals.add(channel);
			}
			if ((mEnabledSensors & SENSOR_EXG1_24BIT)>0){
				String unit = "mVolts";
				if (isEXGUsingDefaultECGConfiguration()){
					channel = new String[]{mMyName,"ECG LL-RA","CAL",unit};
					listofSignals.add(channel);
					channel = new String[]{mMyName,"ECG LL-RA","RAW","no units"};
					listofSignals.add(channel);
					
					channel = new String[]{mMyName,"ECG LL-RA","CAL",unit};
					listofSignals.add(channel);
					channel = new String[]{mMyName,"ECG LL-RA","RAW","no units"};
					listofSignals.add(channel);
				}
				else if (isEXGUsingDefaultEMGConfiguration()){
					channel = new String[]{mMyName,"EMG CH1","CAL",unit};
					listofSignals.add(channel);
					channel = new String[]{mMyName,"EMG CH1","RAW","no units"};
					listofSignals.add(channel);
					
					channel = new String[]{mMyName,"EMG CH2","CAL",unit};
					listofSignals.add(channel);
					channel = new String[]{mMyName,"EMG CH2","RAW","no units"};
					listofSignals.add(channel);
				} else {
					channel = new String[]{mMyName,"EXG1 CH1","CAL",unit};
					listofSignals.add(channel);
					channel = new String[]{mMyName,"EXG1 CH1","RAW","no units"};
					listofSignals.add(channel);
					
					channel = new String[]{mMyName,"EXG1 CH2","CAL",unit};
					listofSignals.add(channel);
					channel = new String[]{mMyName,"EXG1 CH2","RAW","no units"};
					listofSignals.add(channel);
				}

			}
			if ((mEnabledSensors & SENSOR_EXG2_24BIT)>0){
				String unit = "mVolts";
				if (isEXGUsingDefaultECGConfiguration()){
					channel = new String[]{mMyName,"EXG2 CH1","CAL",unit};
					listofSignals.add(channel);
					channel = new String[]{mMyName,"EXG2 CH1","RAW","no units"};
					listofSignals.add(channel);
					
					channel = new String[]{mMyName,"ECG Vx-RL","CAL",unit};
					listofSignals.add(channel);
					channel = new String[]{mMyName,"ECG Vx-RL","RAW","no units"};
					listofSignals.add(channel);
				}
				else if (isEXGUsingDefaultEMGConfiguration()){
					channel = new String[]{mMyName,"EXG2 CH1","CAL",unit};
					listofSignals.add(channel);
					channel = new String[]{mMyName,"EXG2 CH1","RAW","no units"};
					listofSignals.add(channel);
					
					channel = new String[]{mMyName,"EXG2 CH2","CAL",unit};
					listofSignals.add(channel);
					channel = new String[]{mMyName,"EXG2 CH2","RAW","no units"};
					listofSignals.add(channel);
				}
				else {
					channel = new String[]{mMyName,"EXG2 CH1","CAL",unit};
					listofSignals.add(channel);
					channel = new String[]{mMyName,"EXG2 CH1","RAW","no units"};
					listofSignals.add(channel);
					
					channel = new String[]{mMyName,"EXG2 CH2","CAL",unit};
					listofSignals.add(channel);
					channel = new String[]{mMyName,"EXG2 CH2","RAW","no units"};
					listofSignals.add(channel);
				
				}
			}
			if ((mEnabledSensors & SENSOR_EXG1_16BIT)>0){
				String unit = "mVolts";
				if (isEXGUsingDefaultECGConfiguration()){
					channel = new String[]{mMyName,"ECG LL-RA","CAL",unit};
					listofSignals.add(channel);
					channel = new String[]{mMyName,"ECG LL-RA","RAW","no units"};
					listofSignals.add(channel);
					
					channel = new String[]{mMyName,"ECG LL-RA","CAL",unit};
					listofSignals.add(channel);
					channel = new String[]{mMyName,"ECG LL-RA","RAW","no units"};
					listofSignals.add(channel);
				}
				else if (isEXGUsingDefaultEMGConfiguration()){
					channel = new String[]{mMyName,"EMG CH1","CAL",unit};
					listofSignals.add(channel);
					channel = new String[]{mMyName,"EMG CH1","RAW","no units"};
					listofSignals.add(channel);
					
					channel = new String[]{mMyName,"EMG CH2","CAL",unit};
					listofSignals.add(channel);
					channel = new String[]{mMyName,"EMG CH2","RAW","no units"};
					listofSignals.add(channel);
				} else {
					channel = new String[]{mMyName,"EXG1 CH1 16Bit","CAL",unit};
					listofSignals.add(channel);
					channel = new String[]{mMyName,"EXG1 CH1 16Bit","RAW","no units"};
					listofSignals.add(channel);
					
					channel = new String[]{mMyName,"EXG1 CH2 16Bit","CAL",unit};
					listofSignals.add(channel);
					channel = new String[]{mMyName,"EXG1 CH2 16Bit","RAW","no units"};
					listofSignals.add(channel);
				}

			}
			if ((mEnabledSensors & SENSOR_EXG2_16BIT)>0){
				String unit = "mVolts";
				if (isEXGUsingDefaultECGConfiguration()){
					channel = new String[]{mMyName,"EXG2 CH1","CAL",unit};
					listofSignals.add(channel);
					channel = new String[]{mMyName,"EXG2 CH1","RAW","no units"};
					listofSignals.add(channel);
					
					channel = new String[]{mMyName,"ECG Vx-RL","CAL",unit};
					listofSignals.add(channel);
					channel = new String[]{mMyName,"ECG Vx-RL","RAW","no units"};
					listofSignals.add(channel);
				}
				else if (isEXGUsingDefaultEMGConfiguration()){
					channel = new String[]{mMyName,"EXG2 CH1","CAL",unit};
					listofSignals.add(channel);
					channel = new String[]{mMyName,"EXG2 CH1","RAW","no units"};
					listofSignals.add(channel);
					
					channel = new String[]{mMyName,"EXG2 CH2","CAL",unit};
					listofSignals.add(channel);
					channel = new String[]{mMyName,"EXG2 CH2","RAW","no units"};
					listofSignals.add(channel);
				}
				else {
					channel = new String[]{mMyName,"EXG2 CH1 16Bit","CAL",unit};
					listofSignals.add(channel);
					channel = new String[]{mMyName,"EXG2 CH1 16Bit","RAW","no units"};
					listofSignals.add(channel);
					
					channel = new String[]{mMyName,"EXG2 CH2 16Bit","CAL",unit};
					listofSignals.add(channel);
					channel = new String[]{mMyName,"EXG2 CH2 16Bit","RAW","no units"};
					listofSignals.add(channel);
				
				}
			}
			if (((mEnabledSensors & 0xFF00) & SENSOR_BRIDGE_AMP) > 0) {
				String unit = "mVolts";
				channel = new String[]{mMyName,"Bridge Amplifier High","CAL",unit};
				listofSignals.add(channel);
				channel = new String[]{mMyName,"Bridge Amplifier High","RAW","no units"};
				listofSignals.add(channel);
				
				channel = new String[]{mMyName,"Bridge Amplifier Low","CAL",unit};
				listofSignals.add(channel);
				channel = new String[]{mMyName,"Bridge Amplifier Low","RAW","no units"};
				listofSignals.add(channel);
			}
		}
		
		if (mExtraSignalProperties != null){
			listofSignals.addAll(mExtraSignalProperties);
		}
		
		return listofSignals;
	}	
	
	public void addExtraSignalProperty(String [] property){
		if (mExtraSignalProperties==null){
			mExtraSignalProperties = new ArrayList<String[]>();
		}
		mExtraSignalProperties.add(property);
	}
	
	public void removeExtraSignalProperty(String [] property){
		for (int i=mExtraSignalProperties.size()-1;i>-1;i--){
			String[]p = mExtraSignalProperties.get(i);
			if (p[0].equals(property[0]) && p[1].equals(property[1]) && p[2].equals(property[2]) && p[3].equals(property[3])){
				mExtraSignalProperties.remove(i);
			}
			
		}
	}
	
	//AlignmentMatrixMag, SensitivityMatrixMag, OffsetVectorMag

	public double[][] getAlignmentMatrixMag(){
		return AlignmentMatrixMag;
	}

	public double[][] getSensitivityMatrixMag(){
		return SensitivityMatrixMag;
	}

	public double[][] getOffsetVectorMatrixMag(){
		return OffsetVectorMag;
	}

	public double[][] getAlighmentMatrixGyro(){
		return AlignmentMatrixGyro;
	}

	public double[][] getSensitivityMatrixGyro(){
		return SensitivityMatrixGyro;
	}

	public double[][] getOffsetVectorMatrixGyro(){
		return OffsetVectorGyro;
	}

	public double[][] getAlighmentMatrixAccel(){
		return AlignmentMatrixAccel;
	}

	public double[][] getSensitivityMatrixAccel(){
		return SensitivityMatrixAccel;
	}

	public double[][] getOffsetVectorMatrixAccel(){
		return OffsetVectorAccel;
	}

	public double[][] getAlighmentMatrixWRAccel(){
		return AlignmentMatrixAccel2;
	}

	public double[][] getSensitivityMatrixWRAccel(){
		return SensitivityMatrixAccel2;
	}

	public double[][] getOffsetVectorMatrixWRAccel(){
		return OffsetVectorAccel2;
	}


	public int getEnabledSensors() {
		return mEnabledSensors;
	}

	public String[] getListofSupportedSensors(){
		String[] sensorNames = null;
		if (mShimmerVersion==SHIMMER_2R || mShimmerVersion==SHIMMER_2){
			sensorNames = Configuration.Shimmer2.ListofCompatibleSensors;
		} else if  (mShimmerVersion==SHIMMER_3){
			sensorNames = Configuration.Shimmer3.ListofCompatibleSensors;
		}
		return sensorNames;
	}

	public static String[] getListofSupportedSensors(int shimmerVersion){
		String[] sensorNames = null;
		if (shimmerVersion==SHIMMER_2R || shimmerVersion==SHIMMER_2){
			sensorNames = Configuration.Shimmer2.ListofCompatibleSensors;
		} else if  (shimmerVersion==SHIMMER_3){
			sensorNames = Configuration.Shimmer3.ListofCompatibleSensors;
		}
		return sensorNames;
	}

	/**
	 * @param enable this enables the calibration of the gyroscope while streaming
	 * @param bufferSize sets the buffersize of the window used to determine the new calibration parameters, see implementation for more details
	 * @param threshold sets the threshold of when to use the incoming data to recalibrate gyroscope offset, this is in degrees, and the default value is 1.2
	 */
	public void enableOnTheFlyGyroCal(boolean enable,int bufferSize,double threshold){
		if (enable){
			mGyroOVCalThreshold=threshold;
			mGyroX=new DescriptiveStatistics(bufferSize);
			mGyroY=new DescriptiveStatistics(bufferSize);
			mGyroZ=new DescriptiveStatistics(bufferSize);
			mGyroXRaw=new DescriptiveStatistics(bufferSize);
			mGyroYRaw=new DescriptiveStatistics(bufferSize);
			mGyroZRaw=new DescriptiveStatistics(bufferSize);
			mEnableOntheFlyGyroOVCal = enable;
		}
	}

	protected int convertEXGGainSettingToValue(int setting){

		if (setting==0){
			return 6;
		} else if (setting==1){
			return 1;
		} else if (setting==2){
			return 2;
		} else if (setting==3){
			return 3;
		} else if (setting==4){
			return 4;
		} else if (setting==5){
			return 8;
		} else if (setting==6){
			return 12;
		}
		else {
			return -1; // -1 means invalid value
		}

	}

	/**
	 * Checks the EXG register bytes, and determines whether default ecg/emg are being used. 
	 * @return 0 for ECG, 1 for EMG, 3 for test signal and 4 for custom
	 */
	public int checkEXGConfiguration(){
		return -1;
	}

	public byte[] getEXG1RegisterContents(){
		return mEXG1Register;
	}

	public byte[] getEXG2RegisterContents(){
		return mEXG2Register;
	}

	protected boolean isEXGUsingDefaultECGConfiguration(){
		boolean using = false;
		if(((mEXG1Register[3] & 15)==0)&&((mEXG1Register[4] & 15)==0)&& ((mEXG2Register[3] & 15)==0)&&((mEXG2Register[4] & 15)==7)){
			using = true;
		}
		return using;
	}

	protected boolean isEXGUsingDefaultTestSignalConfiguration(){
		boolean using = false;
		if(((mEXG1Register[3] & 15)==5)&&((mEXG1Register[4] & 15)==5)&& ((mEXG2Register[3] & 15)==5)&&((mEXG2Register[4] & 15)==5)){
			using = true;
		}
		return using;
	}

	protected boolean isEXGUsingDefaultEMGConfiguration(){
		boolean using = false;
		if(((mEXG1Register[3] & 15)==9)&&((mEXG1Register[4] & 15)==0)&& ((mEXG2Register[3] & 15)==1)&&((mEXG2Register[4] & 15)==1)){
			using = true;
		}
		return using;
	}

	public byte[] getPressureRawCoefficients(){
		return mPressureCalRawParams;
	}
	
	protected int getExg1CH1GainValue(){
		return mEXG1CH1GainValue;
	}
	
	protected int getExg1CH2GainValue(){
		return mEXG1CH2GainValue;
	}
	
	protected int getExg2CH1GainValue(){
		return mEXG2CH1GainValue;
	}
	
	protected int getExg2CH2GainValue(){
		return mEXG2CH2GainValue;
	}
	
	public String parseReferenceElectrodeTotring(int referenceElectrode){
		String refElectrode = "Unknown";
		
		if(referenceElectrode==0 && (isEXGUsingDefaultECGConfiguration() || isEXGUsingDefaultEMGConfiguration()))
			refElectrode = "Fixed Potential";
		else if(referenceElectrode==13 && isEXGUsingDefaultECGConfiguration())
			refElectrode = "Inverse Wilson CT";
		else if(referenceElectrode==3 && isEXGUsingDefaultEMGConfiguration())
			refElectrode = "Inverse of Ch1";
		
		return refElectrode;
	}
	
	public String parseLeadOffComparatorTresholdToString(int treshold){
		
		String tresholdString="";
		switch(treshold){
			case 0:
				tresholdString = "Pos:95% - Neg:5%";
			break;
			case 1:
				tresholdString = "Pos:92.5% - Neg:7.5%";
			break;
			case 2:
				tresholdString = "Pos:90% - Neg:10%";
			break;
			case 3:
				tresholdString = "Pos:87.5% - Neg:12.5%";
			break;
			case 4:
				tresholdString = "Pos:85% - Neg:15%";
			break;
			case 5:
				tresholdString = "Pos:80% - Neg:20%";
			break;
			case 6:
				tresholdString = "Pos:75% - Neg:25%";
				break;
			case 7:
				tresholdString = "Pos:70% - Neg:30%";
			break;
			default:
				tresholdString = "Treshold unread";
			break;
		}
		
		return tresholdString;
	}
	
	public String parseLeadOffModeToString(int leadOffMode){
		
		String modeString="";
		switch(leadOffMode){
			case 0:
				modeString +="Off";
			break;
			case 1:
				modeString +="DC Current";
			break;
			case 2:
				modeString +="AC Current";
			break;
			default:
				modeString +="No mode selected";
			break;
		}
		
		return modeString;
	}
	
	public String parseLeadOffDetectionCurrentToString(int current){
		
		String currentString="";
		switch(current){
			case 0:
				currentString +="6 nA";
			break;
			case 1:
				currentString +="22 nA";
			break;
			case 2:
				currentString +="6 uA";
			break;
			default:
				currentString +="22 uA";
			break;
		}
		
		return currentString;
	}

}
