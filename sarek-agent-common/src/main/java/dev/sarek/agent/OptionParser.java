package dev.sarek.agent;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class encapsulates and parses Java agent command line options {@code MY_OPTIONS} following the
 * {@code =} (equals) character in {@code -javaagent:/path/to/my-agent.jar=MY_OPTIONS}.
 *
 * @see #parse(String)
 */
public class OptionParser {
  public static final Pattern PT_IDENTIFIER = Pattern.compile("\\p{Alnum}+");

  private final String agentId;
  private final Set<String> legalOptionNames;

  /**
   * Creates an option parser for a given agent ID and set of known option names recognised by a specific
   * {@link Agent}.
   *
   * @param agentId     agent ID for which to parse the options
   * @param optionNames set of known option names
   * @throws IllegalAgentIdException    if <i>agentId</i> does not match {@link #PT_IDENTIFIER}
   * @throws IllegalOptionNameException if any of the <i>optionNames</i> does not match {@link #PT_IDENTIFIER}
   */
  public OptionParser(String agentId, String... optionNames)
    throws IllegalAgentIdException, IllegalOptionNameException
  {
    this.agentId = parseAgentId(agentId);
    this.legalOptionNames = parseOptionNames(optionNames);
  }

  private String parseAgentId(String agentId) throws IllegalAgentIdException {
    if (agentId == null)
      throw new IllegalAgentIdException("agent ID must not be null");
    agentId = agentId.trim();
    if (!PT_IDENTIFIER.matcher(agentId).matches())
      throw new IllegalAgentIdException("agent ID '" + agentId + "' must match PT_IDENTIFIER pattern");
    return agentId;
  }

  private Set<String> parseOptionNames(String[] optionNames) throws IllegalOptionNameException {
    Set<String> legalOptionNames = new HashSet<>();
    for (String optionName : optionNames) {
      if (optionName == null)
        throw new IllegalOptionNameException("option name must not be null");
      optionName = optionName.trim();
      if (!PT_IDENTIFIER.matcher(optionName).matches())
        throw new IllegalOptionNameException("option name '" + optionName + "' must match PT_IDENTIFIER pattern");
      legalOptionNames.add(optionName);
    }
    return legalOptionNames;
  }

  /**
   * Option strings have a simple format for single agents and an extended format for multi agents (i.e. one options
   * string needs to configure multiple agents started from the same JAR):
   * <ul>
   *   <li>
   *     Simple format: {@code name1,name2=value2,name3,name4=value4}
   *   </li>
   *   <li>
   *     Extended format: {@code agentId1{name1,name2=value2,name3,name4=value4};agentId2{name1,name2=value2}}
   *   </li>
   * </ul>
   * <b>Please note:</b>
   * <ul>
   *   <li>
   *     The options parser has a very simple implementation. Thus, agent IDs, option names and values
   *     must not contain any of the separator characters {@code { } , ; =}.
   *   </li>
   *   <li>
   *     Furthermore, agent IDs and option names must be alphanumeric ASCII (regular expression {@code [A-Za-z0-9]+})
   *     and are case-sensitive, so please mind your spelling. E.g. agent ID {@code myAgent} is not the same as
   *     {@code myagent} and name  {@code logDebug} is not the same as {@code LoGdEBuG}.
   *   </li>
   *   <li>
   *     Option names not contained in the set specified in the constructor and their corresponding values will be
   *     silently ignored and not be part of the return value.
   *   </li>
   *   <li>
   *     Option values are optional. If an option name has not value such as {@code myOption} it is interpreted as a
   *     quasi boolean option and its value will be set to the string {@code "true"} (not to the value {@code null}).
   *     However, it is permissible to use {@code myOption=} in order to initialise the value with an empty string
   *     {@code ""}.
   *   </li>
   *   <li>
   *     The options parser tries to handle space characters outside of option values gracefully, i.e. it would ignore
   *     them in instances such as {@code name1 , name2 =value2, name3,name4 =value4} or
   *     {@code agentId1 { name1,name2 =value2, name3,name4=value4} ; agentId2 { name1, name2 =value2}}.
   *   </li>
   *   <li>
   *     Space characters inside option values are being preserved, also leading and trailing ones, just in case they
   *     have a special meaning for the corresponding agent, such as {@code indent=  } (value consists of two spaces) or
   *     {@code logPrefix=[Special Agent] }.
   *   </li>
   *   <li>
   *     If the same agent ID or option name (per agent) occurs multiple times, no error is raised but no specific
   *     result is guaranteed with regard to merging or ignoring options.
   *   </li>
   *   <li>
   *     If option parsing is lenient in some regard nopt mentioned here, please do not rely on it to stay like that in
   *     the future.
   *   </li>
   * </ul>
   */
  public Map<String, String> parse(String optionString) {
    Map<String, String> options = new HashMap<>();
    // No options -> do nothing
    if (optionString == null)
      return options;

    // Extended format -> try to find configuration matching agent ID
    if (optionString.contains("{")) {
      Pattern ptExtendedFormat = Pattern.compile(agentId + "\\s*\\{(.*)}");
      // Replace multi agent options in extended format by simple format for this agent ID
      optionString = Arrays
        .stream(optionString.split("\\s*;\\s*"))
        .filter(agentOptions -> agentOptions.startsWith(agentId + "{"))
        .map(agentOptions -> ptExtendedFormat.matcher(agentOptions).group(1))
        .findFirst()
        .orElse(null);
    }

    // Multi-agent config, but no configuration for this particular agent ID found
    if (optionString == null)
      return options;

    // Extract option key/value pairs from simple format
    Pattern ptKeyWithOptionalValue = Pattern.compile("\\s*(\\p{Alnum}+)\\s*(=(.*))?");
    Arrays
      .stream(optionString.split(","))
      .forEach(keyValuePair -> {
        System.out.println("keyValuePair = " + keyValuePair);
        Matcher matcher = ptKeyWithOptionalValue.matcher(keyValuePair);
        if (matcher.matches()) {
          String key = matcher.group(1);
          if (legalOptionNames.contains(key)) {
            String value = matcher.group(3);
            options.put(key, value != null ? value : "true");
          }
        }
      });

    return options;
  }

  public static class IllegalAgentIdException extends AgentException {
    public IllegalAgentIdException(String message) {
      super(message);
    }
  }

  public static class IllegalOptionNameException extends AgentException {
    public IllegalOptionNameException(String message) {
      super(message);
    }
  }
}
