package dev.sarek.app;

public class SubWithComplexConstructor extends BaseWithComplexConstructor {
  public SubWithComplexConstructor(byte aByte, char aChar, double aDouble, float aFloat, int anInt, long aLong, short aShort, boolean aBoolean, String string, int[][] ints) {
    super(aByte, aChar, aDouble, aFloat, anInt, aLong, aShort, aBoolean, string, ints);
    System.out.println("Creating SubWithComplexConstructor");
  }

  @Override
  public String toString() {
    return super.toString().replaceFirst("^Base", "Sub");
  }
}
