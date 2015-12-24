package data;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Data implements Comparable<Data>{

	private long timeStamp;
	private Map<String,Double> data;
	
	public Data (long timeStamp, String tag, double value){
		
		data = new HashMap<String,Double>();
		
		this.timeStamp = timeStamp;
		this.data.put(tag, value);
	}
	
	public long getTimeStamp() {
		return timeStamp;
	}

	public double getData(String tag) {
		return this.data.get(tag);
	}

	public Set<String> getTags() {
		return this.data.keySet();
	}

	public void addData(String tag, Double value){
		this.data.put(tag,value);
	}
	public int compareTo(Data d){
		if(this.timeStamp > d.timeStamp){
			return 1;
		}
		else if(this.timeStamp < d.timeStamp){
			return -1;
		}
		else{
			return 0;
		}
	}
	
	public boolean equals(Object d){
		
		if((d == null) || this.getClass() != d.getClass()){
			return false;
		}
		else if(this.timeStamp != ((Data)d).timeStamp){
			return false;
		}
		else return true;
	}
	
	public int hashCode(){
		//TODO properly implement this.
		return (int) this.timeStamp;
	}
}
