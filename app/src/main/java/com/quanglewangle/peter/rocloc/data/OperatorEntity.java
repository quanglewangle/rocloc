package com.quanglewangle.peter.rocloc.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "operators")
public class OperatorEntity {
    @PrimaryKey
    @NonNull
    public String callsign = "";

    public String name;      // fname + " " + lname from QRZ
    public String fname;
    public String lname;
    public String city;
    public String state;
    public String country;
    public double lat;
    public double lon;
    public String grid;
    public double altm;      // altitude metres ASL (0 = unknown)
    public long lastSynced;
}
