
public class Vector3d {
    public double x;
    public double y;
    public double z;

    public Vector3d(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public double x() {
        return this.x;
    }

    public double y() {
        return this.y;
    }

    public double z() {
        return this.z;
    }

    public Vector3d set(Vector3d v) {
        this.x = v.x();
        this.y = v.y();
        this.z = v.z();
        return this;
    }

    public Vector3d set(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

    public Vector3d sub(Vector3d v) {
    	Vector3d ret = new Vector3d(x, y, z);
        ret.x = ret.x - v.x();
        ret.y = ret.y - v.y();
        ret.z = ret.z - v.z();
        return ret;
    }

    public Vector3d add(Vector3d v) {
    	Vector3d ret = new Vector3d(x, y, z);
        ret.x = ret.x + v.x();
        ret.y = ret.y + v.y();
        ret.z = ret.z + v.z();
        return ret;
    }

    public Vector3d mul(Vector3d v) {
    	Vector3d ret = new Vector3d(x, y, z);
        ret.x = ret.x * v.x();
        ret.y = ret.y * v.y();
        ret.z = ret.z * v.z();
        return ret;
    }
    
    public Vector3d mul(double scalar) {
    	Vector3d ret = new Vector3d(x, y, z);
        ret.x = ret.x * scalar;
        ret.y = ret.y * scalar;
        ret.z = ret.z * scalar;
        return ret;
    }

    public Vector3d div(Vector3d v) {
    	Vector3d ret = new Vector3d(x, y, z);
        ret.x = ret.x / v.x();
        ret.y = ret.y / v.y();
        ret.z = ret.z / v.z();
        return ret;
    }

    /**
     * Rotate this vector the specified radians around the X axis.
     * 
     * @param angle
     *          the angle in radians
     * @return this
     */
    public Vector3d rotateX(double angle) {
        double sin = Math.sin(angle);
        double cos = Math.cosFromSin(sin, angle);
        double y = this.y * cos - this.z * sin;
        double z = this.y * sin + this.z * cos;
        this.y = y;
        this.z = z;
        return this;
    }

    /**
     * Rotate this vector the specified radians around the Y axis.
     * 
     * @param angle
     *          the angle in radians
     * @return this
     */
    public Vector3d rotateY(double angle) {
        double sin = Math.sin(angle);
        double cos = Math.cosFromSin(sin, angle);
        double x =  this.x * cos + this.z * sin;
        double z = -this.x * sin + this.z * cos;
        this.x = x;
        this.z = z;
        return this;
    }

    /**
     * Rotate this vector the specified radians around the Z axis.
     * 
     * @param angle
     *          the angle in radians
     * @return this
     */
    public Vector3d rotateZ(double angle) {
        double sin = Math.sin(angle);
        double cos = Math.cosFromSin(sin, angle);
        double x = this.x * cos - this.y * sin;
        double y = this.x * sin + this.y * cos;
        this.x = x;
        this.y = y;
        return this;
    }

    public Vector3d div(double scalar) {
    	Vector3d ret = new Vector3d(x, y, z);
        double inv = 1.0 / scalar;
        ret.x = ret.x * inv;
        ret.y = ret.y * inv;
        ret.z = ret.z * inv;
        return ret;
    }
    
    public Vector3d normalize() {
        double invLength = Math.invsqrt(Math.fma(x, x, Math.fma(y, y, z * z)));
        this.x = x * invLength;
        this.y = y * invLength;
        this.z = z * invLength;
        return this;
    }
    
    public Vector3d cross(Vector3d v) {
    	Vector3d ret = new Vector3d(x, y, z);
        double rx = Math.fma(y, v.z(), -z * v.y());
        double ry = Math.fma(z, v.x(), -x * v.z());
        double rz = Math.fma(x, v.y(), -y * v.x());
        ret.x = rx;
        ret.y = ry;
        ret.z = rz;
        return ret;
    }
    
    public double dot(Vector3d v) {
        return Math.fma(this.x, v.x(), Math.fma(this.y, v.y(), this.z * v.z()));
    }
    
    public double length() {
        return Math.sqrt(Math.fma(x, x, Math.fma(y, y, z * z)));
    }
    
    public double distance(Vector3d v) {
    	return java.lang.Math.sqrt(java.lang.Math.pow(v.x - x, 2) + java.lang.Math.pow(v.y - y, 2) + java.lang.Math.pow(v.z - z, 2));
    }
    
}