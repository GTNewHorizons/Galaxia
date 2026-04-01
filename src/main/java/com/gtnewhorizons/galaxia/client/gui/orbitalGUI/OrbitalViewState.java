package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

final class OrbitalViewState {

    double cameraX;
    double cameraY;
    double zoomLevel;
    double targetCameraX;
    double targetCameraY;
    double targetZoomLevel;
    double isometricProgress;
    double targetIsometricProgress;

    OrbitalViewState(double initialZoom) {
        this.zoomLevel = initialZoom;
        this.targetZoomLevel = initialZoom;
    }

    void step(double lerpSpeed) {
        cameraX = lerp(cameraX, targetCameraX, lerpSpeed);
        cameraY = lerp(cameraY, targetCameraY, lerpSpeed);
        zoomLevel = lerp(zoomLevel, targetZoomLevel, lerpSpeed);
        isometricProgress = lerp(isometricProgress, targetIsometricProgress, lerpSpeed);
    }

    void snap(double threshold) {
        if (Math.abs(cameraX - targetCameraX) < threshold) {
            cameraX = targetCameraX;
        }
        if (Math.abs(cameraY - targetCameraY) < threshold) {
            cameraY = targetCameraY;
        }
        if (Math.abs(zoomLevel - targetZoomLevel) < threshold) {
            zoomLevel = targetZoomLevel;
        }
        if (Math.abs(isometricProgress - targetIsometricProgress) < threshold) {
            isometricProgress = targetIsometricProgress;
        }
    }

    void reset(boolean resetCameraToOrigin) {
        isometricProgress = 0.0;
        targetIsometricProgress = 0.0;
        if (resetCameraToOrigin) {
            setCamera(0.0, 0.0);
        }
    }

    void setCamera(double x, double y) {
        cameraX = x;
        cameraY = y;
        targetCameraX = x;
        targetCameraY = y;
    }

    void syncToTargets() {
        cameraX = targetCameraX;
        cameraY = targetCameraY;
        zoomLevel = targetZoomLevel;
        isometricProgress = targetIsometricProgress;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
