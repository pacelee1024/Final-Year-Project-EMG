package com.mkyong.util;
//Update
//Update----finish

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.TimeUnit;
public class Send_data {


	public static void main(String[] args) throws IOException {

		Send_data obj = new Send_data();
		obj.run();
	}


	public static void run() throws IOException {

		String csvFile = "C:\\CompuTrainer 3D V3\\Rider Performance\\LASTPERF.3DP.txt"; //Log file from the computrainer
		BufferedReader br = null;
		String line = "";
		long c=0;
		Path path = Paths.get("C:\\CompuTrainer 3D V3\\Rider Performance\\lastperf.3dp"); //Path to get the unix time stamp 
		
		long start_time;
		//To get the total number of lines in the log file
		LineNumberReader  lnr = new LineNumberReader(new FileReader(new File("C:\\CompuTrainer 3D V3\\Rider Performance\\LASTPERF.3DP.txt")));
		lnr.skip(Long.MAX_VALUE);
		long lines = lnr.getLineNumber()-1;
		//System.out.println(lnr.getLineNumber() + 1); //Add 1 because line index starts at 0
		// Finally, the LineNumberReader object should be closed to prevent resource leak
		lnr.close();
		

		// To read the last modified time of the file 
		BasicFileAttributes attributes = null;
		try {
			attributes = Files.readAttributes(path, BasicFileAttributes.class);
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		FileTime time = attributes.lastModifiedTime();
		
		// Converting the time to unix time format
		
		long milliseconds = attributes.lastModifiedTime().to(TimeUnit.MILLISECONDS);
		System.out.println("Time creation time " + time);
		System.out.println("Time in milliseconds "+ milliseconds);

		try {

			br = new BufferedReader(new FileReader(csvFile));
			while ((line = br.readLine()) != null) {
				
				c++;
				
				
				if(c > 62){ // 62 because the data starts from this line
					
					
					// use comma as separator
					String[] computrainer = line.split("\\s+");

					//System.out.println(computrainer.length);
					for(int i=1; i < computrainer.length;i++){
						if (i == 1 && c == lines){
							milliseconds = (milliseconds - Long.valueOf((String)computrainer[i]).longValue()); // Getting the end time from the log file
						//System.out.println("Time=" + computrainer[i]);
						}
							//if (i == 2)
							//System.out.println("Power=" + computrainer[i]);	
						//Ank
						//long unixTime = (System.currentTimeMillis());
						//long new_time = unixTime;
					}
					
					for(int i = 1;i<computrainer.length;i++) {    //Sending the data to the http server
						
						try {

							if (i == 2)
							{

								long new_time = Long.valueOf((String)computrainer[1]).longValue();
								long unixTime = new_time + milliseconds;
								
								URL url = new URL("http://mobilecare.i2r.a-star.edu.sg:8080/WbsnServices/webapi/power/pedal/"+unixTime+"?value="+computrainer[2]+"&sessionid=2");
								System.out.println("Unixtime=" + unixTime);
								System.out.println("Power " + computrainer[2]);
								
								//URL obj = new URL(url);
								HttpURLConnection con = (HttpURLConnection) url.openConnection();						 
								// optional default is GET
								con.setRequestMethod("GET");						 
								int responseCode = con.getResponseCode();
								
/*								InputStream response = url.openStream();
								new InputStreamReader(response);

								System.out.println("Response is "+response);
								java.util.Scanner s = new java.util.Scanner(response).useDelimiter("\\A");
								String w = s.hasNext() ? s.next() : "";
								System.out.println(w + "\n");
*/
								 

										
							}
						} 
						catch (MalformedURLException e) {
							e.printStackTrace();
						} 
						catch (IOException e) {
							e.printStackTrace();
						}

						try {
							Thread.sleep(200);
						} 
						catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}

					}
				}
			}

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}



		System.out.println("Done");
	}

}
