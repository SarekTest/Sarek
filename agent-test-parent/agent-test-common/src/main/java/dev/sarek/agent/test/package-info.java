/**
 * In this package we can put all test classes which should not be transformed by global transformers such as
 * "remove final" and "constructor mock". We can avoid instrumentation because dev.sarek.agent..* is on global
 * instrumentation black lists for both transformers.
 */
package dev.sarek.agent.test;
