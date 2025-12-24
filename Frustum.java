import java.awt.geom.Point2D;

public class Frustum {
    public double fov;
    public double aspectRatio;
    public double near;
    public double far;

    public double cameraX;
    public double cameraY;
    public double cameraZ;

    // yaw/pitch in DEGREES, same as Space.yaw / Space.pitch
    public double cameraYaw;
    public double cameraPitch;

    private double[] projectionMatrix;

    public Frustum(double fov, double aspectRatio, double near, double far) {
        this.fov = fov;
        this.aspectRatio = aspectRatio;
        this.near = near;
        this.far = far;
        updateProjectionMatrix();
    }

    public void setCameraPosition(double x, double y, double z) {
        this.cameraX = x;
        this.cameraY = y;
        this.cameraZ = z;
    }

    /**
     * Shared yaw/pitch -> forward/right/up for both view matrix and movement.
     * yawDeg  : rotation around world Y axis (degrees)
     * pitchDeg: looking up/down (degrees)
     *
     * World up is (0, 1, 0).
     * yaw = 0, pitch = 0  => looking along +Z.
     */
    public static void computeCameraBasis(double yawDeg, double pitchDeg,
                                          double[] forward, double[] right, double[] up) {
        double yawRad   = Math.toRadians(yawDeg);
        double pitchRad = Math.toRadians(pitchDeg);

        double cosYaw   = Math.cos(yawRad);
        double sinYaw   = Math.sin(yawRad);
        double cosPitch = Math.cos(pitchRad);
        double sinPitch = Math.sin(pitchRad);

        // Forward vector from spherical coordinates:
        // yaw around Y, then pitch up/down
        double fx = cosPitch * Math.sin(yawRad);
        double fy = sinPitch;
        double fz = cosPitch * Math.cos(yawRad);

        // World up
        double upx = 0.0, upy = 1.0, upz = 0.0;

        // Right = worldUp × forward
        double rx = upy * fz - upz * fy;
        double ry = upz * fx - upx * fz;
        double rz = upx * fy - upy * fx;
        double rLen = Math.sqrt(rx*rx + ry*ry + rz*rz);
        if (rLen != 0.0) {
            rx /= rLen; ry /= rLen; rz /= rLen;
        }

        // Up = forward × right  (ensures orthonormal basis, no roll)
        double ux = fy * rz - fz * ry;
        double uy = fz * rx - fx * rz;
        double uz = fx * ry - fy * rx;
        double uLen = Math.sqrt(ux*ux + uy*uy + uz*uz);
        if (uLen != 0.0) {
            ux /= uLen; uy /= uLen; uz /= uLen;
        }

        // Normalize forward too, just to be safe
        double fLen = Math.sqrt(fx*fx + fy*fy + fz*fz);
        if (fLen != 0.0) {
            fx /= fLen; fy /= fLen; fz /= fLen;
        }

        forward[0] = fx; forward[1] = fy; forward[2] = fz;
        right[0]   = rx; right[1]   = ry; right[2]   = rz;
        up[0]      = ux; up[1]      = uy; up[2]      = uz;
    }

    /**
     * Build view matrix (column-major) from camera position and yaw/pitch.
     * Camera looks along 'forward'; camera-space Z axis is -forward.
     */
    public double[] computeViewMatrix() {
        double[] forward = new double[3];
        double[] right   = new double[3];
        double[] up      = new double[3];

        computeCameraBasis(cameraYaw, cameraPitch, forward, right, up);

        double fx = forward[0], fy = forward[1], fz = forward[2];
        double rx = right[0],   ry = right[1],   rz = right[2];
        double ux = up[0],      uy = up[1],      uz = up[2];

        // Camera-space Z axis points along -forward
        double fvx = -fx, fvy = -fy, fvz = -fz;

        double[] view = new double[16];

        // Column-major 4x4 view matrix
        view[0]  = rx;   view[1]  = ry;   view[2]  = rz;   view[3]  = 0.0;
        view[4]  = ux;   view[5]  = uy;   view[6]  = uz;   view[7]  = 0.0;
        view[8]  = fvx;  view[9]  = fvy;  view[10] = fvz;  view[11] = 0.0;

        double cx = cameraX, cy = cameraY, cz = cameraZ;

        // Translation part: -R^T * C
        view[12] = -(rx * cx + ux * cy + fvx * cz);
        view[13] = -(ry * cx + uy * cy + fvy * cz);
        view[14] = -(rz * cx + uz * cy + fvz * cz);
        view[15] = 1.0;

        return view;
    }

    public void updateProjectionMatrix() {
        double f = 1.0 / Math.tan(Math.toRadians(fov) / 2.0);

        projectionMatrix = new double[16];

        projectionMatrix[0]  = f / aspectRatio;
        projectionMatrix[1]  = 0.0;
        projectionMatrix[2]  = 0.0;
        projectionMatrix[3]  = 0.0;

        projectionMatrix[4]  = 0.0;
        projectionMatrix[5]  = f;
        projectionMatrix[6]  = 0.0;
        projectionMatrix[7]  = 0.0;

        projectionMatrix[8]  = 0.0;
        projectionMatrix[9]  = 0.0;
        projectionMatrix[10] = (far + near) / (near - far);
        projectionMatrix[11] = -1.0;

        projectionMatrix[12] = 0.0;
        projectionMatrix[13] = 0.0;
        projectionMatrix[14] = (2.0 * far * near) / (near - far);
        projectionMatrix[15] = 0.0;
    }

    public double[] worldToCameraSpace(double x, double y, double z, double[] viewMatrix) {
        double[] cameraSpacePosition = new double[4];
        cameraSpacePosition[0] = viewMatrix[0] * x + viewMatrix[4] * y + viewMatrix[8]  * z + viewMatrix[12];
        cameraSpacePosition[1] = viewMatrix[1] * x + viewMatrix[5] * y + viewMatrix[9]  * z + viewMatrix[13];
        cameraSpacePosition[2] = viewMatrix[2] * x + viewMatrix[6] * y + viewMatrix[10] * z + viewMatrix[14];
        cameraSpacePosition[3] = viewMatrix[3] * x + viewMatrix[7] * y + viewMatrix[11] * z + viewMatrix[15];
        return cameraSpacePosition;
    }
    
//    public double[] worldToCameraSpaceDirect(double x, double y, double z) {
//        // 1) Compute basis from current camera orientation
//        double[] f = new double[3];
//        double[] r = new double[3];
//        double[] u = new double[3];
//        Frustum.computeCameraBasis(cameraYaw, cameraPitch, f, r, u);
//
//        double fx = f[0], fy = f[1], fz = f[2];
//        double rx = r[0], ry = r[1], rz = r[2];
//        double ux = u[0], uy = u[1], uz = u[2];
//
//        // 2) Vector from camera to point in world space
//        double dx = x - cameraX;
//        double dy = y - cameraY;
//        double dz = z - cameraZ;
//
//        // 3) Project onto camera basis:
//        // camera X = dot(dir, right)
//        // camera Y = dot(dir, up)
//        // camera Z = dot(dir, -forward)  (looking along -Z in camera space)
//        double camX = dx * rx + dy * ry + dz * rz;
//        double camY = dx * ux + dy * uy + dz * uz;
//        double camZ = dx * (-fx) + dy * (-fy) + dz * (-fz);
//
//        return new double[] { camX, camY, camZ, 1.0 };
//    }
    
    public void worldToCameraSpaceDirect(double x, double y, double z, double[] out4) {
        // Compute camera basis (no array alloc)
        double yaw = java.lang.Math.toRadians(cameraYaw);
        double pitch = java.lang.Math.toRadians(cameraPitch);

        double cosPitch = java.lang.Math.cos(pitch);
        double fx = java.lang.Math.sin(yaw) * cosPitch;
        double fy = java.lang.Math.sin(pitch);
        double fz = java.lang.Math.cos(yaw) * cosPitch;

        // right = worldUp(0,1,0) x forward = (fz, 0, -fx)
        double rx = fz, ry = 0.0, rz = -fx;
        double rLen = java.lang.Math.sqrt(rx*rx + ry*ry + rz*rz);
        if (rLen < 1e-12) rLen = 1e-12;
        rx /= rLen; ry /= rLen; rz /= rLen;

        // up = forward x right
        double ux = fy*rz - fz*ry;
        double uy = fz*rx - fx*rz;
        double uz = fx*ry - fy*rx;

        // Vector from camera to point
        double dx = x - cameraX;
        double dy = y - cameraY;
        double dz = z - cameraZ;

        // Project onto basis
        double camX = dx * rx + dy * ry + dz * rz;
        double camY = dx * ux + dy * uy + dz * uz;
        double camZ = dx * (-fx) + dy * (-fy) + dz * (-fz);

        out4[0] = camX;
        out4[1] = camY;
        out4[2] = camZ;
        out4[3] = 1.0;
    }
    
    public double[] worldToCameraSpaceDirect(double x, double y, double z) {
        double[] out = new double[4];
        worldToCameraSpaceDirect(x, y, z, out);
        return out;
    }


    public Point2D.Double clipSpaceToScreenSpace(double x, double y, double z, double w,
                                                 int screenWidth, int screenHeight) {
        if (w > 0) {
            double projectedX = x / w;
            double projectedY = y / w;

            double screenX = (projectedX * 0.5 + 0.5) * screenWidth;
            double screenY = (-projectedY * 0.5 + 0.5) * screenHeight;

            return new Point2D.Double(screenX, screenY);
        } else {
            // Behind camera
            return null;
        }
    }
    
    public boolean clipSpaceToScreenSpace(double x, double y, double z, double w,
            int screenWidth, int screenHeight,
            Point2D.Double out) {
		if (w <= 0.0) return false;
		
		double projectedX = x / w;
		double projectedY = y / w;
		
		out.x = (projectedX * 0.5 + 0.5) * screenWidth;
		out.y = (-projectedY * 0.5 + 0.5) * screenHeight;
		return true;
	}

    public Point2D.Double project3DTo2D(double x, double y, double z,
                                        int screenWidth, int screenHeight) {
        // Multiply camera-space coordinates by projection matrix
        double[] clipSpacePosition = new double[4];
        clipSpacePosition[0] = projectionMatrix[0] * x + projectionMatrix[4] * y + projectionMatrix[8]  * z + projectionMatrix[12];
        clipSpacePosition[1] = projectionMatrix[1] * x + projectionMatrix[5] * y + projectionMatrix[9]  * z + projectionMatrix[13];
        clipSpacePosition[2] = projectionMatrix[2] * x + projectionMatrix[6] * y + projectionMatrix[10] * z + projectionMatrix[14];
        clipSpacePosition[3] = projectionMatrix[3] * x + projectionMatrix[7] * y + projectionMatrix[11] * z + projectionMatrix[15];

        return clipSpaceToScreenSpace(
            clipSpacePosition[0], clipSpacePosition[1], clipSpacePosition[2], clipSpacePosition[3],
            screenWidth, screenHeight
        );
    }
    
    public boolean project3DTo2D(double x, double y, double z,
            int screenWidth, int screenHeight,
            Point2D.Double out) {
		// Compute clip coords without allocating double[4]
		double clipX = projectionMatrix[0] * x + projectionMatrix[4] * y + projectionMatrix[8]  * z + projectionMatrix[12];
		double clipY = projectionMatrix[1] * x + projectionMatrix[5] * y + projectionMatrix[9]  * z + projectionMatrix[13];
		double clipZ = projectionMatrix[2] * x + projectionMatrix[6] * y + projectionMatrix[10] * z + projectionMatrix[14];
		double clipW = projectionMatrix[3] * x + projectionMatrix[7] * y + projectionMatrix[11] * z + projectionMatrix[15];
		
		return clipSpaceToScreenSpace(clipX, clipY, clipZ, clipW, screenWidth, screenHeight, out);
	}

    
    public static double computePixelRadiusByProjection(
            Frustum fr, double worldX, double worldY, double worldZ,
            double radiusUnits, Point2D.Double centerScreen)
    {
        // Build camera basis (same logic as Frustum.computeCameraBasis)
        double yaw = Math.toRadians(fr.cameraYaw);
        double pitch = Math.toRadians(fr.cameraPitch);

        double fx = Math.sin(yaw) * Math.cos(pitch);
        double fy = Math.sin(pitch);
        double fz = Math.cos(yaw) * Math.cos(pitch);

        // right = worldUp x forward
        double rx = 1.0 * fz - 0.0 * fy;   // (0,1,0) x (fx,fy,fz) = (fz,0,-fx)
        double ry = 0.0;
        double rz = -fx;

        double rLen = Math.sqrt(rx*rx + ry*ry + rz*rz);
        if (rLen < 1e-12) return 0.0;
        rx /= rLen; ry /= rLen; rz /= rLen;

        // boundary point in world space
        double bx = worldX + rx * radiusUnits;
        double by = worldY + ry * radiusUnits;
        double bz = worldZ + rz * radiusUnits;

        double[] camB = fr.worldToCameraSpaceDirect(bx, by, bz);
        Point2D.Double bScreen = fr.project3DTo2D(camB[0], camB[1], camB[2], Space.VIEW_WIDTH, Space.VIEW_HEIGHT);
        if (bScreen == null) return 0.0;

        double dx = bScreen.x - centerScreen.x;
        double dy = bScreen.y - centerScreen.y;
        return Math.sqrt(dx*dx + dy*dy);
    }
}
