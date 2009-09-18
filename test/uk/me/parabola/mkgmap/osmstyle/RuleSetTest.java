/*
 * Copyright (c) 2009.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 or
 * version 2 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

package uk.me.parabola.mkgmap.osmstyle;

import java.io.Reader;
import java.io.StringReader;

import uk.me.parabola.mkgmap.general.LevelInfo;
import uk.me.parabola.mkgmap.reader.osm.GType;
import uk.me.parabola.mkgmap.reader.osm.Way;

import static org.junit.Assert.*;
import org.junit.Test;

/**
 * More tests for rule sets. Mostly concentrating on ordering issues and
 * not on the resulting type.
 * 
 * @see RuleFileReaderTest
 */
public class RuleSetTest {
	private final String MAXSPEED_EXAMPLE = "highway=* & maxspeed=40mph {set mcssl=40}" +
			"highway=primary & mcssl=40 [0x01]" +
			"highway=* & mcssl=40 [0x02]" +
			"highway=primary [0x3]";

	/**
	 * A test for matching in the correct order with a simple set
	 * of tags.  See also the next test.
	 */
	@Test
	public void testFirstMatch1() {
		RuleSet rs = makeRuleSet("c=d & a=b [0x1]" +
				"a=b & c=d [0x2]" +
				"a=b [0x3]");

		Way el = new Way(1);
		el.addTag("a", "b");
		el.addTag("c", "d");
		GType type = rs.resolveType(el);
		assertNotNull("should be found", type);
		assertEquals("first matching rule wins", 1, type.getType());
	}
	/**
	 * As previous test but with order reversed.  Depending on the order
	 * that the tags iterate from the way, you might get different results.
	 */
	@Test
	public void testFirstMatch2() {
		RuleSet rs = makeRuleSet("a=b & c=d [0x1]" +
				"c=d & a=b [0x2]" +
				"a=b [0x3]");

		Way el = new Way(1);
		el.addTag("a", "b");
		el.addTag("c", "d");
		GType type = rs.resolveType(el);
		assertNotNull("should be found", type);
		assertEquals("first matching rule wins", 1, type.getType());
	}

	/**
	 * An action variable is set on a rule that starts with an exists clause.
	 * We then attempt to match on value that it is 
	 * @throws Exception
	 */
	@Test
	public void testActionVarSetOnExistsRule1() throws Exception {
		RuleSet rs = makeRuleSet(MAXSPEED_EXAMPLE);

		Way el = new Way(1);
		el.addTag("highway", "primary");
		el.addTag("maxspeed", "40mph");
		el.addTag("ref", "A123");
		el.addTag("name", "Long Lane");

		GType type = rs.resolveType(el);
		assertEquals("should match first", 1, type.getType());
	}

	@Test
	public void testActionVarSetOnExistsRule2() throws Exception {
		RuleSet rs = makeRuleSet(MAXSPEED_EXAMPLE);

		Way el = new Way(1);
		el.addTag("highway", "unclassified");
		el.addTag("maxspeed", "40mph");
		el.addTag("ref", "A123");
		el.addTag("name", "Long Lane");

		GType type = rs.resolveType(el);
		assertEquals("should match first", 2, type.getType());
	}

	/**
	 * Check that actions are run in the order given.  Use the add command
	 * to set a variable.  The first add that is run will be the value of
	 * the variable.
	 */
	@Test
	public void testActionOrder() {
		RuleSet rs = makeRuleSet("b=c {add fred=1}" +
				"a=b {add fred=2}" +
				"c=d {add fred=3}" +
				"a=b [0x1]");

		// All of the conditions are set.
		Way el = new Way(1);
		el.addTag("a", "b");
		el.addTag("b", "c");
		el.addTag("c", "d");

		rs.resolveType(el);
		assertEquals("b=c was first action", "1", el.getTag("fred"));
	}

	/**
	 * Match on a tag that was set in a previous action rule and was not
	 * on the original element.
	 */
	@Test
	public void testMatchOnSetTag() {
		RuleSet rs = makeRuleSet("highway=yes {set abcxyz = 1}" +
				"abcxyz=1 [0x1]");

		Way el = new Way(1);
		el.addTag("highway", "yes");

		GType type = rs.resolveType(el);
		assertNotNull("type matched on previously set tag", type);
	}

	/**
	 * A chain of rules, some of which contain tags from the element and
	 * some that contain only tags that are set in previous rules.
	 */
	@Test
	public void testOrderChain() {
		RuleSet rs = makeRuleSet("z=1 {add fred=1;}" +
				"fred=1 {add abba=1}" +
				"z=1 & abba=1 {add destiny=1}" +
				"destiny=1 [0x1]" +
				"z=1 [0x2]");

		Way el = new Way(1);
		el.addTag("z", "1");

		GType type = rs.resolveType(el);
		assertNotNull("chain of commands", type);
		assertEquals("'destiny' should be selected", 1, type.getType());
	}

	/**
	 * A chain of rules, some of which contain tags from the element and
	 * some that contain only tags that are set in previous rules.
	 */
	@Test
	public void testOrderChain2() {
		RuleSet rs = makeRuleSet("z=1 {add fred=1;}" +
				"fred=1 {add abba=1}" +
				"abba=1 {add destiny=1}" +
				"destiny=1 [0x1]");

		Way el = new Way(1);
		el.addTag("z", "1");

		GType type = rs.resolveType(el);
		assertNotNull("chain of commands", type);
	}

	/**
	 * Append to a variable in the correct order as in the rule set.
	 */
	@Test
	public void testAppendInOrder() {
		RuleSet rs = makeRuleSet("highway=primary {set R='${R} a'}" +
				"ref=A1 {set R='${R} b'}" +
				"z=1 {set R='${R} c'}" +
				"a=1 {set R='${R} d'}");

		Way el = new Way(1);
		el.addTag("R", "init");
		el.addTag("highway", "primary");
		el.addTag("ref", "A1");
		el.addTag("z", "1");
		el.addTag("a", "1");

		rs.resolveType(el);
		String s = el.getTag("R");
		assertEquals("appended value", "init a b c d", s);
	}

	/**
	 * Rules should only be evaluated once for an element.
	 */
	@Test
	public void testRuleEvaluatedOnce() {
		RuleSet rs = makeRuleSet(
				"z=1 {set highway=secondary}" +
				"highway=secondary {set R='ABC ${R}';}" +
				"R='ABC a' [0x1]" +
				"R='ABC ABC a' [0x2]");

		Way el = new Way(1);
		el.addTag("R", "a");
		el.addTag("z", "1");
		el.addTag("highway", "secondary");

		GType type = rs.resolveType(el);
		System.out.println(el.getTag("R"));
		assertNotNull(type);
	}

	@Test
	public void testCheckinExample() throws Exception {
		RuleSet rs = makeRuleSet("highway=motorway  {set blue=true;}\n" +
				"blue=true  [0x1 ]\n" +
				"highway=motorway [0x2]");

		Way el = new Way(1);
		el.addTag("highway", "motorway");

		GType type = rs.resolveType(el);
		assertEquals("first match is on blue", 1, type.getType());
	}

	/**
	 * Create a rule set out of a string.  The string is processed
	 * as if it were in a file and the levels spec had been set.
	 */
	private RuleSet makeRuleSet(String in) {
		Reader r = new StringReader(in);

		RuleSet rs = new RuleSet();
		RuleFileReader rr = new RuleFileReader(GType.POLYLINE, LevelInfo.createFromString("0:24 1:20 2:18 3:16 4:14"), rs);
		rr.load(r, "string");
		return rs;
	}
}
