/**
 * 
 */
package pojoDemo;

/**
 * @author HouKangxi
 *
 */
public class Response {
	private int id;
	private long time;

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public long getTime() {
		return time;
	}

	public void setTime(long time) {
		this.time = time;
	}

	@Override
	public String toString() {
		return "Response [id=" + id + ", time=" + time + "]";
	}

}
