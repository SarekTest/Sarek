package dev.sarek.app;

public class StringWrapper {
  private String text;

  public String replaceAll(String regex, String replacement) {
    return text.replaceAll(regex, replacement);
  }

  public StringWrapper(String text) {
    this.text = text;
  }

  public String replace(CharSequence target, CharSequence replacement) {
    return text.replace(target, replacement);
  }
}
