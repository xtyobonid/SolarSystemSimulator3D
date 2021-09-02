
public class Plane {
	
	public Vector3d a;
	public Vector3d b; 
	public Vector3d c;

	public void set3Points(Vector3d ta, Vector3d tb, Vector3d tc) {
		a = ta;
		b = tb;
		c = tc;
	}
	
	public Vector3d normal() {
		Vector3d dir = b.sub(a).cross(c.sub(a));
		Vector3d normal = dir.div(dir.length());
		return normal;
	}
	
	//https://mathinsight.org/distance_point_plane
	//http://www.lighthouse3d.com/tutorials/maths/plane/
	public double distance(Vector3d p) {
		
		Vector3d v = b.sub(a);
		Vector3d u = c.sub(a);
		
		Vector3d n = v.cross(u);
		
		n.normalize();
		
		double A = n.x;
		double B = n.y;
		double C = n.z;
		double D = -(n.dot(a));
		
		double distance = (A * p.x) + (B * p.y) + (C * p.z) + D;
		
		return distance;
	}

}
