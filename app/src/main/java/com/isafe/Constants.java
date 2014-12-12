package com.isafe;

/**
 * Created by rvenkataraman on 16/12/14.
 */
public enum Constants {
    START_STOP_JOURNEY_TOGGLE_STATE("StartStopJourneyToggleState");

    String id;

    Constants(String id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return id;
    }
}
