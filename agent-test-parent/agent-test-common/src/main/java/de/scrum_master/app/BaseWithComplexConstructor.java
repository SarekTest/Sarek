package de.scrum_master.app;

import java.util.Arrays;

public class BaseWithComplexConstructor {
  private byte aByte;
  private char aChar;
  private double aDouble;
  private float aFloat;
  private int anInt;
  private long aLong;
  private short aShort;
  private boolean aBoolean;
  private String string;
  private int[][] ints;

  public BaseWithComplexConstructor(byte aByte, char aChar, double aDouble, float aFloat, int anInt, long aLong, short aShort, boolean aBoolean, String string, int[][] ints) {
    System.out.println("Creating BaseWithComplexConstructor");
    this.aByte = aByte;
    this.aChar = aChar;
    this.aDouble = aDouble;
    this.aFloat = aFloat;
    this.anInt = anInt;
    this.aLong = aLong;
    this.aShort = aShort;
    this.aBoolean = aBoolean;
    this.string = string;
    this.ints = ints;
  }

  @Override
  public String toString() {
    return "BaseWithComplexConstructor(" +
      "aByte=" + aByte +
      ", aChar=" + aChar +
      ", aDouble=" + aDouble +
      ", aFloat=" + aFloat +
      ", anInt=" + anInt +
      ", aLong=" + aLong +
      ", aShort=" + aShort +
      ", aBoolean=" + aBoolean +
      ", string='" + string + '\'' +
      ", ints=" + Arrays.toString(ints) +
      ')';
  }
}
