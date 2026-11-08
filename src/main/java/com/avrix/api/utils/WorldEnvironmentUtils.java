package com.avrix.api.utils;

import com.avrix.api.permissions.PermissionsManager;
import zombie.GameTime;
import zombie.characters.Capability;
import zombie.characters.IsoPlayer;
import zombie.iso.weather.ClimateManager;
import zombie.network.GameServer;

/**
 * Helpers for world time, calendar and weather.
 * <p>
 * Calendar values exposed by this class are one-based: months are {@code 1-12}
 * and days are {@code 1-31}.
 */
public final class WorldEnvironmentUtils {

    private WorldEnvironmentUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Returns the current world year.
     *
     * @return world year
     */
    public static int getYear() {
        return GameTime.getInstance().getYear();
    }

    /**
     * Returns the current world month.
     *
     * @return month in the range {@code 1-12}
     */
    public static int getMonth() {
        return GameTime.getInstance().getMonth() + 1;
    }

    /**
     * Returns the current world day of month.
     *
     * @return day in the range {@code 1-31}
     */
    public static int getDay() {
        return GameTime.getInstance().getDayPlusOne();
    }

    /**
     * Returns the current world hour.
     *
     * @return hour in the range {@code 0-23}
     */
    public static int getHour() {
        return GameTime.getInstance().getHour();
    }

    /**
     * Returns the current world minute.
     *
     * @return minute in the range {@code 0-59}
     */
    public static int getMinute() {
        return GameTime.getInstance().getMinutes();
    }

    /**
     * Returns the time of day as a fractional hour.
     *
     * @return time of day, for example {@code 13.5} for 13:30
     */
    public static float getTimeOfDay() {
        return GameTime.getInstance().getTimeOfDay();
    }

    /**
     * Sets the world date and time and synchronizes the clock with clients.
     *
     * @param year   world year
     * @param month  month in the range {@code 1-12}
     * @param day    day in the range {@code 1-31}
     * @param hour   hour in the range {@code 0-23}
     * @param minute minute in the range {@code 0-59}
     * @return {@code true} if the values were valid and applied
     */
    public static boolean setTime(int year, int month, int day, int hour, int minute) {
        if (year <= 0 || month < 1 || month > 12 || day < 1 || day > 31
                || hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            return false;
        }

        GameTime time = GameTime.getInstance();
        time.setYear(year);
        time.setMonth(month - 1);
        time.setDay(day - 1);
        time.setTimeOfDay(hour + (minute / 60.0f));
        if (GameServer.server && GameServer.udpEngine != null) {
            GameServer.syncClock();
        }
        return true;
    }

    /**
     * Sets the world date and time after checking the actor's capability.
     *
     * @param actor  player performing the action
     * @param year   world year
     * @param month  month in the range {@code 1-12}
     * @param day    day in the range {@code 1-31}
     * @param hour   hour in the range {@code 0-23}
     * @param minute minute in the range {@code 0-59}
     * @return {@code true} if the actor is allowed and the values were applied
     */
    public static boolean setTime(IsoPlayer actor, int year, int month, int day, int hour, int minute) {
        return PermissionsManager.hasCapability(actor, Capability.ClimateManager)
                && setTime(year, month, day, hour, minute);
    }

    /**
     * Checks whether it is currently raining.
     *
     * @return {@code true} if rain is active
     */
    public static boolean isRaining() {
        ClimateManager climate = getClimate();
        return climate != null && climate.isRaining();
    }

    /**
     * Returns the current rain intensity.
     *
     * @return rain intensity in the range {@code 0-1}, or {@code 0} when it is not raining
     */
    public static float getRainIntensity() {
        ClimateManager climate = getClimate();
        return climate == null ? 0.0f : climate.getRainIntensity();
    }

    /**
     * Returns the current air temperature.
     *
     * @return temperature in degrees Celsius
     */
    public static float getTemperature() {
        ClimateManager climate = getClimate();
        return climate == null ? 0.0f : climate.getTemperature();
    }

    /**
     * Returns the current wind speed.
     *
     * @return wind speed in kilometres per hour
     */
    public static float getWindSpeedKph() {
        ClimateManager climate = getClimate();
        return climate == null ? 0.0f : climate.getWindspeedKph();
    }

    /**
     * Starts rain with the given intensity.
     *
     * @param intensity rain intensity in the range {@code 0-1}
     * @return {@code true} if the weather update was sent
     */
    public static boolean startRain(float intensity) {
        ClimateManager climate = getClimate();
        if (!GameServer.server || climate == null) {
            return false;
        }
        climate.transmitServerStartRain(Math.max(0.0f, Math.min(1.0f, intensity)));
        return true;
    }

    /**
     * Starts rain after checking the actor's capability.
     *
     * @param actor     player performing the action
     * @param intensity rain intensity in the range {@code 0-1}
     * @return {@code true} if the actor is allowed and the weather update was sent
     */
    public static boolean startRain(IsoPlayer actor, float intensity) {
        return PermissionsManager.hasCapability(actor, Capability.StartStopRain)
                && startRain(intensity);
    }

    /**
     * Stops rain while keeping other weather effects active.
     *
     * @return {@code true} if the weather update was sent
     */
    public static boolean stopRain() {
        ClimateManager climate = getClimate();
        if (!GameServer.server || climate == null) {
            return false;
        }
        climate.transmitServerStopRain();
        return true;
    }

    /**
     * Stops rain after checking the actor's capability.
     *
     * @param actor player performing the action
     * @return {@code true} if the actor is allowed and the weather update was sent
     */
    public static boolean stopRain(IsoPlayer actor) {
        return PermissionsManager.hasCapability(actor, Capability.StartStopRain) && stopRain();
    }

    /**
     * Stops rain and thunder.
     *
     * @return {@code true} if the weather update was sent
     */
    public static boolean stopWeather() {
        ClimateManager climate = getClimate();
        if (!GameServer.server || climate == null) {
            return false;
        }
        climate.transmitServerStopWeather();
        return true;
    }

    /**
     * Stops rain and thunder after checking the actor's capability.
     *
     * @param actor player performing the action
     * @return {@code true} if the actor is allowed and the weather update was sent
     */
    public static boolean stopWeather(IsoPlayer actor) {
        return PermissionsManager.hasCapability(actor, Capability.StartStopRain) && stopWeather();
    }

    /**
     * Resolves the initialized climate manager.
     *
     * @return climate manager, or {@code null} before world load
     */
    private static ClimateManager getClimate() {
        return ClimateManager.getInstance();
    }
}
