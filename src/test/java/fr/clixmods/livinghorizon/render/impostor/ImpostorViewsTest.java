package fr.clixmods.livinghorizon.render.impostor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImpostorViewsTest {
    @Test
    void facingTheCameraShowsPictureZero() {
        // A figure at the origin facing south (yaw 0), the camera due south of it.
        assertEquals(0, ImpostorViews.view(0, 0, 100));
        // Facing west (yaw 90), the camera due west.
        assertEquals(0, ImpostorViews.view(90, -100, 0));
    }

    @Test
    void seenFromBehindShowsTheOppositePicture() {
        assertEquals(ImpostorViews.VIEWS / 2, ImpostorViews.view(0, 0, -100));
        assertEquals(ImpostorViews.VIEWS / 2, ImpostorViews.view(90, 100, 0));
    }

    @Test
    void turningTheFigureTurnsThePicture() {
        int first = ImpostorViews.view(0, 0, 100);
        int turned = ImpostorViews.view(45, 0, 100);
        assertEquals(Math.floorMod(first - 1, ImpostorViews.VIEWS), turned);
    }

    @Test
    void everyAngleGivesAValidPicture() {
        for (int yaw = -720; yaw <= 720; yaw += 7) {
            for (int angle = 0; angle < 360; angle += 5) {
                double dx = Math.cos(Math.toRadians(angle)) * 300, dz = Math.sin(Math.toRadians(angle)) * 300;
                int view = ImpostorViews.view(yaw, dx, dz);
                assertTrue(view >= 0 && view < ImpostorViews.VIEWS, "yaw " + yaw + " angle " + angle + " -> " + view);
            }
        }
    }

    @Test
    void aFigureWaveringRoundAnEdgeKeepsItsPicture() {
        // The camera due south; the edge between pictures 0 and 1 is at 22.5 degrees.
        int shown = ImpostorViews.view(-20, 0, 100, -1);
        assertEquals(0, shown);
        for (double yaw = -20; yaw >= -22.5 - ImpostorViews.STICK + 0.5; yaw -= 0.5) {
            assertEquals(shown, ImpostorViews.view(yaw, 0, 100, shown), "yaw " + yaw);
        }
        assertEquals(1, ImpostorViews.view(-22.5 - ImpostorViews.STICK - 1, 0, 100, shown));
    }

    @Test
    void aPictureFarFromTheRightOneIsDropped() {
        assertEquals(ImpostorViews.VIEWS / 2, ImpostorViews.view(0, 0, -100, 0));
    }

    @Test
    void eachPictureIsBakedTurnedAStepFurther() {
        assertEquals(180f, ImpostorViews.facing(0));
        for (int v = 1; v < ImpostorViews.VIEWS; v++) {
            assertEquals(360f / ImpostorViews.VIEWS, ImpostorViews.facing(v - 1) - ImpostorViews.facing(v), 1e-4f);
        }
    }

    @Test
    void aFigureTurnedAsAPictureWasBakedShowsThatPicture() {
        // The bake's camera stands north of the figure, looking along +z.
        for (int v = 0; v < ImpostorViews.VIEWS; v++) assertEquals(v, ImpostorViews.view(ImpostorViews.facing(v), 0, -10));
    }

    @Test
    void aLongBodyGetsAWiderTile() {
        assertTrue(ImpostorViews.worldSize(1.0, 0.9) > ImpostorViews.worldSize(1.0, 0.5));
    }

    @Test
    void aFlyerIsPicturedFromTheNearestHeight() {
        assertEquals(0, ImpostorViews.height(-89));
        assertEquals(0, ImpostorViews.height(-50));
        assertEquals(1, ImpostorViews.height(-40));
        assertEquals(1, ImpostorViews.height(-20));
        assertEquals(2, ImpostorViews.height(-5));
        assertEquals(2, ImpostorViews.height(10));
        assertEquals(3, ImpostorViews.height(20));
        assertEquals(3, ImpostorViews.height(89));
    }
}
