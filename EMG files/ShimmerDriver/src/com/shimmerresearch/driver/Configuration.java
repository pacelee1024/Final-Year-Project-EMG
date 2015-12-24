/*Rev 2.6
 * 
 * 
 *  Copyright (c) 2010, Shimmer Research, Ltd.
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
 * @date   May, 2014
 * 
 * The purpose of this code is to maintain the configurations of BTSTREAM
 * 
 * 
 * Changes since 2.5 (RM first revision)
 * - Addition of Strain Gauge for Shimmer3
 * 
 * Changes since 2.2
 * - Changed list of compatible sensors to public
 * 
 */


package com.shimmerresearch.driver;

import java.util.HashMap;
import java.util.Map;

public class Configuration {
	//Channel Contents
	public static class Shimmer3{
		public class Channel{
			public final static int XAAccel     			 = 0x00;
			public final static int YAAccel    				 = 0x01;
			public final static int ZAAccel     			 = 0x02;
			public final static int VBatt       			 = 0x03;
			public final static int XDAccel     			 = 0x04;
			public final static int YDAccel     			 = 0x05;
			public final static int ZDAccel     			 = 0x06;
			public final static int XMag        			 = 0x07;
			public final static int YMag        			 = 0x08;
			public final static int ZMag        			 = 0x09;
			public final static int XGyro       			 = 0x0A;
			public final static int YGyro       			 = 0x0B;
			public final static int ZGyro       			 = 0x0C;
			public final static int ExtAdc7					 = 0x0D;
			public final static int ExtAdc6					 = 0x0E;
			public final static int ExtAdc15 				 = 0x0F;
			public final static int IntAdc1					 = 0x10;
			public final static int IntAdc12 				 = 0x11;
			public final static int IntAdc13 				 = 0x12;
			public final static int IntAdc14 				 = 0x13;
			public final static int XAlterAccel      		 = 0x14; //Alternative Accelerometer
			public final static int YAlterAccel     		 = 0x15;
			public final static int ZAlterAccel     		 = 0x16;
			public final static int XAlterMag        		 = 0x17; //Alternative Magnetometer
			public final static int YAlterMag        		 = 0x18;
			public final static int ZAlterMag        		 = 0x19;
			public final static int Temperature 			 = 0x1A;
			public final static int Pressure 				 = 0x1B;
			public final static int GsrRaw 					 = 0x1C;
			public final static int EXG_ADS1292R_1_STATUS 	 = 0x1D;
			public final static int EXG_ADS1292R_1_CH1_24BIT = 0x1E;
			public final static int EXG_ADS1292R_1_CH2_24BIT = 0x1F;
			public final static int EXG_ADS1292R_2_STATUS 	 = 0x20;
			public final static int EXG_ADS1292R_2_CH1_24BIT = 0x21;
			public final static int EXG_ADS1292R_2_CH2_24BIT = 0x22;
			public final static int EXG_ADS1292R_1_CH1_16BIT = 0x23;
			public final static int EXG_ADS1292R_1_CH2_16BIT = 0x24;
			public final static int EXG_ADS1292R_2_CH1_16BIT = 0x25;
			public final static int EXG_ADS1292R_2_CH2_16BIT = 0x26;
			public final static int BridgeAmpHigh  			 = 0x27;
			public final static int BridgeAmpLow   			 = 0x28;
			
		}

		public class SensorBitmap{
			//Sensor Bitmap for Shimmer 3
			public static final int SENSOR_A_ACCEL_S3			   = 0x80;
			public static final int SENSOR_GYRO_S3			   	   = 0x40;
			public static final int SENSOR_MAG_S3				   = 0x20;
			public static final int SENSOR_EXG1_24BIT			   = 0x10;
			public static final int SENSOR_EXG2_24BIT			   = 0x08;
			public static final int SENSOR_GSR					   = 0x04;
			public static final int SENSOR_EXT_A7				   = 0x02;
			public static final int SENSOR_EXT_A6				   = 0x01;
			public static final int SENSOR_VBATT_S3				   = 0x2000;
			public static final int SENSOR_D_ACCEL_S3			   = 0x1000;
			public static final int SENSOR_EXT_A15				   = 0x0800;
			public static final int SENSOR_INT_A1				   = 0x0400;
			public static final int SENSOR_INT_A12				   = 0x0200;
			public static final int SENSOR_INT_A13				   = 0x0100;
			public static final int SENSOR_INT_A14				   = 0x800000;
			public static final int SENSOR_BMP180				   = 0x40000;
			public static final int SENSOR_EXG1_16BIT			   = 0x100000;
			public static final int SENSOR_EXG2_16BIT			   = 0x080000;
			public static final int SENSOR_BRIDGE_AMP			   = 0x8000;
		}

		public final static String[] ListofCompatibleSensors={"Low Noise Accelerometer","Wide Range Accelerometer","Gyroscope","Magnetometer","Battery Voltage","External ADC A7","External ADC A6","External ADC A15","Internal ADC A1","Internal ADC A12","Internal ADC A13","Internal ADC A14","Pressure","GSR","EXG1","EXG2","EXG1 16Bit","EXG2 16Bit", "Bridge Amplifier"}; 
		public final static String[] ListofAccelRange={"+/- 2g","+/- 4g","+/- 8g","+/- 16g"};
		public final static String[] ListofGyroRange={"250dps","500dps","1000dps","2000dps"}; 
		public final static String[] ListofMagRange={"+/- 1.3Ga","+/- 1.9Ga","+/- 2.5Ga","+/- 4.0Ga","+/- 4.7Ga","+/- 5.6Ga","+/- 8.1Ga"}; 
		public final static String[] ListofPressureResolution={"Low","Standard","High","Very High"};
		public final static String[] ListofGSRRange={"10kOhm to 56kOhm","56kOhm to 220kOhm","220kOhm to 680kOhm","680kOhm to 4.7MOhm","Auto Range"};
		public final static String[] ListofDefaultEXG={"ECG","EMG","Test Signal"};
		public final static String[] ListOfExGGain={"6","1","2","3","4","8","12"};
		public final static String[] ListOfECGReferenceElectrode={"Inverse Wilson CT","Fixed Potential"};
		public final static String[] ListOfEMGReferenceElectrode={"Fixed Potential", "Inverse of Ch1"};
		public final static String[] ListOfExGLeadOffDetection={"Off","DC Current"};
		public final static String[] ListOfExGLeadOffCurrent={"6 nA","22 nA", "6 uA", "22 uA"};
		public final static String[] ListOfExGLeadOffComparator={"Pos:95%-Neg:5%","Pos:92.5%-Neg:7.5%","Pos:90%-Neg:10%","Pos:87.5%-Neg:12.5%","Pos:85%-Neg:15%","Pos:80%-Neg:20%","Pos:75%-Neg:25%","Pos:70%-Neg:30%"};
		
	}
	public static class Shimmer2{
		public class Channel{
			public final static int XAccel      = 0x00;
			public final static int YAccel      = 0x01;
			public final static int ZAccel      = 0x02;
			public final static int XGyro       = 0x03;
			public final static int YGyro       = 0x04;
			public final static int ZGyro       = 0x05;
			public final static int XMag        = 0x06;
			public final static int YMag        = 0x07;
			public final static int ZMag        = 0x08;
			public final static int EcgRaLl     = 0x09;
			public final static int EcgLaLl     = 0x0A;
			public final static int GsrRaw      = 0x0B;
			public final static int GsrRes      = 0x0C;
			public final static int Emg         = 0x0D;
			public final static int AnExA0      = 0x0E;
			public final static int AnExA7      = 0x0F;
			public final static int BridgeAmpHigh  = 0x10;
			public final static int BridgeAmpLow   = 0x11;
			public final static int HeartRate   = 0x12;
		}
		public class SensorBitmap{
			public static final int SENSOR_ACCEL				   = 0x80;
			public static final int SENSOR_GYRO				   	   = 0x40;
			public static final int SENSOR_MAG					   = 0x20;
			public static final int SENSOR_ECG					   = 0x10;
			public static final int SENSOR_EMG					   = 0x08;
			public static final int SENSOR_GSR					   = 0x04;
			public static final int SENSOR_EXP_BOARD_A7		       = 0x02;
			public static final int SENSOR_EXP_BOARD_A0		       = 0x01;
			public static final int SENSOR_BRIDGE_AMP			   = 0x8000;
			public static final int SENSOR_HEART				   = 0x4000;

		}
		public final static String[] ListofCompatibleSensors={"Accelerometer","Gyroscope","Magnetometer","Battery Voltage","ECG","EMG","GSR","Exp Board","Bridge Amplifier","Heart Rate"};
		public final static String[] ListofAccelRange={"+/- 1.5g","+/- 6g"};
		public final static String[] ListofMagRange={"+/- 0.8Ga","+/- 1.3Ga","+/- 1.9Ga","+/- 2.5Ga","+/- 4.0Ga","+/- 4.7Ga","+/- 5.6Ga","+/- 8.1Ga"};
		public final static String[] ListofGSRRange={"10kOhm to 56kOhm","56kOhm to 220kOhm","220kOhm to 680kOhm","680kOhm to 4.7MOhm","Auto Range"};
	}



}


