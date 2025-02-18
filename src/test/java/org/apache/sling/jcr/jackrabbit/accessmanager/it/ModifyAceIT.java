/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sling.jcr.jackrabbit.accessmanager.it;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Stream;

import javax.json.JsonArray;
import javax.json.JsonException;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.json.JsonValue.ValueType;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.NameValuePair;
import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.oak.spi.security.authorization.accesscontrol.AccessControlConstants;
import org.apache.jackrabbit.oak.spi.security.authorization.restriction.RestrictionProvider;
import org.apache.jackrabbit.oak.spi.security.privilege.PrivilegeConstants;
import org.apache.sling.servlets.post.JSONResponse;
import org.apache.sling.servlets.post.PostResponseCreator;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceRegistration;

/**
 * Tests for the 'modifyAce' Sling Post Operation
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class ModifyAceIT extends AccessManagerClientTestSupport {

    private ServiceRegistration<PostResponseCreator> serviceReg;
    private ServiceRegistration<RestrictionProvider> serviceReg2;
    private VerifyAce verifyTrue = jsonValue -> assertEquals(ValueType.TRUE, jsonValue.getValueType());

    @Before
    @Override
    public void before() throws Exception {
        Bundle bundle = FrameworkUtil.getBundle(getClass());
        Dictionary<String, Object> props = new Hashtable<>(); // NOSONAR
        serviceReg = bundle.getBundleContext().registerService(PostResponseCreator.class,
                new CustomPostResponseCreatorImpl(), props);
        serviceReg2 = bundle.getBundleContext().registerService(RestrictionProvider.class,
                new CustomRestrictionProviderImpl(), props);

        super.before();
    }

    @After
    @Override
    public void after() throws Exception {
        if (serviceReg != null) {
            serviceReg.unregister();
        }
        if (serviceReg2 != null) {
            serviceReg2.unregister();
        }

        super.after();
    }

    @Test
    public void testModifyAceForUser() throws IOException, JsonException {
        testUserId = createTestUser();

        testFolderUrl = createTestFolder();

        // update the ACE
        List<NameValuePair> postParams = new AcePostParamsBuilder(testUserId)
                .withPrivilege(PrivilegeConstants.JCR_READ, PrivilegeValues.ALLOW)
                .withPrivilege(PrivilegeConstants.JCR_WRITE, PrivilegeValues.DENY)
                .withPrivilege(PrivilegeConstants.JCR_MODIFY_ACCESS_CONTROL, PrivilegeValues.BOGUS)//invalid value should be ignored.
                .build();
        addOrUpdateAce(testFolderUrl, postParams);

        JsonObject aceObject = getAce(testFolderUrl, testUserId);
        assertNotNull(aceObject);

        int order = aceObject.getInt("order");
        assertEquals(0, order);

        JsonObject privilegesObject = aceObject.getJsonObject("privileges");
        assertNotNull(privilegesObject);
        assertEquals(2, privilegesObject.size());
        //allow
        assertPrivilege(privilegesObject, true, PrivilegeValues.ALLOW, PrivilegeConstants.JCR_READ);
        //deny
        assertPrivilege(privilegesObject, true, PrivilegeValues.DENY, PrivilegeConstants.JCR_WRITE);
    }

    /**
     * Test for SLING-7831
     */
    @Test
    public void testModifyAceCustomPostResponse() throws IOException, JsonException {
        testUserId = createTestUser();

        testFolderUrl = createTestFolder();

        // update the ACE
        List<NameValuePair> postParams = new AcePostParamsBuilder(testUserId)
                .with(":responseType", "custom")
                .withPrivilege(PrivilegeConstants.JCR_READ, PrivilegeValues.ALLOW)
                .withPrivilege(PrivilegeConstants.JCR_WRITE, PrivilegeValues.DENY)
                .withPrivilege(PrivilegeConstants.JCR_MODIFY_ACCESS_CONTROL, PrivilegeValues.BOGUS)//invalid value should be ignored.
                .build();
        String content = addOrUpdateAce(testFolderUrl, postParams, CONTENT_TYPE_HTML);
        assertEquals("Thanks!", content); //verify that the content matches the custom response
    }

    @Test
    public void testModifyAceForGroup() throws IOException, JsonException {
        testGroupId = createTestGroup();

        testFolderUrl = createTestFolder();

        // update the ACE
        List<NameValuePair> postParams = new AcePostParamsBuilder(testGroupId)
                .withPrivilege(PrivilegeConstants.JCR_READ, PrivilegeValues.ALLOW)
                .withPrivilege(PrivilegeConstants.JCR_WRITE, PrivilegeValues.DENY)
                .withPrivilege(PrivilegeConstants.JCR_MODIFY_ACCESS_CONTROL, PrivilegeValues.BOGUS)//invalid value should be ignored.
                .build();
        addOrUpdateAce(testFolderUrl, postParams);

        JsonObject aceObject = getAce(testFolderUrl, testGroupId);
        assertNotNull(aceObject);

        int order = aceObject.getInt("order");
        assertEquals(0, order);

        String principalString = aceObject.getString("principal");
        assertEquals(testGroupId, principalString);

        JsonObject privilegesObject = aceObject.getJsonObject("privileges");
        assertNotNull(privilegesObject);
        assertEquals(2, privilegesObject.size());
        //allow privileges
        assertPrivilege(privilegesObject, true, PrivilegeValues.ALLOW, PrivilegeConstants.JCR_READ);
        //deny privileges
        assertPrivilege(privilegesObject, true, PrivilegeValues.DENY, PrivilegeConstants.JCR_WRITE);
    }

    /**
     * Test for SLING-997, preserve privileges that were not posted with the modifyAce
     * request.
     */
    @Test
    public void testMergeAceForUser() throws IOException, JsonException {
        testUserId = createTestUser();
        testFolderUrl = createTestFolder();

        // update the ACE
        List<NameValuePair> postParams = new AcePostParamsBuilder(testUserId)
                .withPrivilege(PrivilegeConstants.JCR_READ, PrivilegeValues.ALLOW)
                .withPrivilege(PrivilegeConstants.JCR_READ_ACCESS_CONTROL, PrivilegeValues.ALLOW)
                .withPrivilege(PrivilegeConstants.JCR_ADD_CHILD_NODES, PrivilegeValues.ALLOW)
                .withPrivilege(PrivilegeConstants.JCR_MODIFY_ACCESS_CONTROL, PrivilegeValues.DENY)
                .withPrivilege(PrivilegeConstants.JCR_REMOVE_CHILD_NODES, PrivilegeValues.DENY)
                .build();
        addOrUpdateAce(testFolderUrl, postParams);

        JsonObject aceObject = getAce(testFolderUrl, testUserId);
        assertNotNull(aceObject);

        String principalString = aceObject.getString("principal");
        assertEquals(testUserId, principalString);

        int order = aceObject.getInt("order");
        assertEquals(0, order);

        JsonObject privilegesObject = aceObject.getJsonObject("privileges");
        assertNotNull(privilegesObject);
        assertEquals(5, privilegesObject.size());
        //allow privileges
        assertPrivilege(privilegesObject, true, PrivilegeValues.ALLOW, PrivilegeConstants.JCR_READ);
        assertPrivilege(privilegesObject, true, PrivilegeValues.ALLOW, PrivilegeConstants.JCR_READ_ACCESS_CONTROL);
        assertPrivilege(privilegesObject, true, PrivilegeValues.ALLOW, PrivilegeConstants.JCR_ADD_CHILD_NODES);
        //deny privileges
        assertPrivilege(privilegesObject, true, PrivilegeValues.DENY, PrivilegeConstants.JCR_MODIFY_ACCESS_CONTROL);
        assertPrivilege(privilegesObject, true, PrivilegeValues.DENY, PrivilegeConstants.JCR_REMOVE_CHILD_NODES);



        //2. post a new set of privileges to merge with the existing privileges
        List<NameValuePair> postParams2 = new AcePostParamsBuilder(testUserId)
                //jcr:read and jcr:addChildNodes are not posted, so they should remain in the granted ACE
                .withPrivilege(PrivilegeConstants.JCR_READ_ACCESS_CONTROL, PrivilegeValues.NONE)
                .withPrivilege(PrivilegeConstants.JCR_MODIFY_PROPERTIES, PrivilegeValues.ALLOW)
                .withPrivilege(PrivilegeConstants.JCR_MODIFY_ACCESS_CONTROL, PrivilegeValues.DENY)
                .withPrivilege(PrivilegeConstants.JCR_REMOVE_CHILD_NODES, PrivilegeValues.NONE)
                .withPrivilege(PrivilegeConstants.JCR_REMOVE_NODE, PrivilegeValues.DENY)
                .build();
        addOrUpdateAce(testFolderUrl, postParams2);

        JsonObject aceObject2 = getAce(testFolderUrl, testUserId);
        assertNotNull(aceObject2);

        String principalString2 = aceObject2.getString("principal");
        assertEquals(testUserId, principalString2);

        JsonObject privilegesObject2 = aceObject2.getJsonObject("privileges");
        assertNotNull(privilegesObject2);
        assertEquals(5, privilegesObject2.size());
        //allow privileges
        assertPrivilege(privilegesObject2, true, PrivilegeValues.ALLOW, PrivilegeConstants.JCR_READ);
        assertPrivilege(privilegesObject2, true, PrivilegeValues.ALLOW, PrivilegeConstants.JCR_ADD_CHILD_NODES);
        assertPrivilege(privilegesObject2, true, PrivilegeValues.ALLOW, PrivilegeConstants.JCR_MODIFY_PROPERTIES);
        //deny privileges
        assertPrivilege(privilegesObject2, true, PrivilegeValues.DENY, PrivilegeConstants.JCR_MODIFY_ACCESS_CONTROL);
        assertPrivilege(privilegesObject2, true, PrivilegeValues.DENY, PrivilegeConstants.JCR_REMOVE_NODE);
    }


    /**
     * Test for SLING-997, preserve privileges that were not posted with the modifyAce
     * request.
     */
    @Test
    public void testMergeAceForUserSplitAggregatePrincipal() throws IOException, JsonException {
        testUserId = createTestUser();
        testFolderUrl = createTestFolder();

        // update the ACE
        List<NameValuePair> postParams = new AcePostParamsBuilder(testUserId)
                .withPrivilege(PrivilegeConstants.JCR_READ, PrivilegeValues.ALLOW)
                .withPrivilege(PrivilegeConstants.JCR_WRITE, PrivilegeValues.DENY)
                .build();
        addOrUpdateAce(testFolderUrl, postParams);

        JsonObject privilegesObject = getAcePrivleges(testFolderUrl, testUserId);
        assertNotNull(privilegesObject);
        assertEquals(2, privilegesObject.size());
        //allow privileges
        assertPrivilege(privilegesObject, true, PrivilegeValues.ALLOW, PrivilegeConstants.JCR_READ);
        //deny privileges
        assertPrivilege(privilegesObject, true, PrivilegeValues.DENY, PrivilegeConstants.JCR_WRITE);


        //2. post a new set of privileges to merge with the existing privileges
        List<NameValuePair> postParams2 = new AcePostParamsBuilder(testUserId)
                //jcr:read is not posted, so it should remain in the granted ACE
                //jcr:write is not posted, but one of the aggregate privileges is now granted, so the aggregate privilege should be disaggregated into
                //  the remaining denied privileges in the denied ACE
                .withPrivilege(PrivilegeConstants.JCR_MODIFY_PROPERTIES, PrivilegeValues.ALLOW)
                .build();
        addOrUpdateAce(testFolderUrl, postParams2);

        JsonObject privilegesObject2 = getAcePrivleges(testFolderUrl, testUserId);
        assertNotNull(privilegesObject2);
        assertEquals(5, privilegesObject2.size());
        //allow privileges
        assertPrivilege(privilegesObject2, true, PrivilegeValues.ALLOW, PrivilegeConstants.JCR_READ);
        assertPrivilege(privilegesObject2, true, PrivilegeValues.ALLOW, PrivilegeConstants.JCR_MODIFY_PROPERTIES);
        //deny privileges
        assertPrivilege(privilegesObject2, true, PrivilegeValues.DENY, PrivilegeConstants.JCR_ADD_CHILD_NODES);
        assertPrivilege(privilegesObject2, true, PrivilegeValues.DENY, PrivilegeConstants.JCR_REMOVE_NODE);
        assertPrivilege(privilegesObject2, true, PrivilegeValues.DENY, PrivilegeConstants.JCR_REMOVE_CHILD_NODES);
    }

    /**
     * Test for SLING-997, preserve privileges that were not posted with the modifyAce
     * request.
     */
    @Test
    public void testMergeAceForUserCombineAggregatePrivilege() throws IOException, JsonException {
        testUserId = createTestUser();
        testFolderUrl = createTestFolder();

        // update the ACE
        List<NameValuePair> postParams = new AcePostParamsBuilder(testUserId)
                .withPrivilege(PrivilegeConstants.JCR_READ, PrivilegeValues.ALLOW)
                .withPrivilege(PrivilegeConstants.JCR_REMOVE_NODE, PrivilegeValues.DENY)
                .build();
        addOrUpdateAce(testFolderUrl, postParams);

        JsonObject aceObject = getAce(testFolderUrl, testUserId);
        assertNotNull(aceObject);

        assertEquals(testUserId, aceObject.getString("principal"));

        JsonObject privilegesObject = aceObject.getJsonObject("privileges");
        assertNotNull(privilegesObject);
        assertEquals(2, privilegesObject.size());
        //allow privileges
        assertPrivilege(privilegesObject, true, PrivilegeValues.ALLOW, PrivilegeConstants.JCR_READ);
        //deny privileges
        assertPrivilege(privilegesObject, true, PrivilegeValues.DENY, PrivilegeConstants.JCR_REMOVE_NODE);



        //2. post a new set of privileges to merge with the existing privileges
        List<NameValuePair> postParams2 = new AcePostParamsBuilder(testUserId)
                //jcr:read is not posted, so it should remain in the granted ACE

                //deny the full jcr:write aggregate privilege, which should merge with the
                //existing part.
                .withPrivilege(PrivilegeConstants.JCR_WRITE, PrivilegeValues.DENY)
                .build();
        addOrUpdateAce(testFolderUrl, postParams2);

        JsonObject aceObject2 = getAce(testFolderUrl, testUserId);
        assertNotNull(aceObject2);

        assertEquals(testUserId, aceObject2.getString("principal"));

        JsonObject privilegesObject2 = aceObject2.getJsonObject("privileges");
        assertNotNull(privilegesObject2);
        assertEquals(2, privilegesObject2.size());
        //allow privileges
        assertPrivilege(privilegesObject2, true, PrivilegeValues.ALLOW, PrivilegeConstants.JCR_READ);
        //deny privileges
        assertPrivilege(privilegesObject2, true, PrivilegeValues.DENY, PrivilegeConstants.JCR_WRITE);
    }


    /**
     * Test ACE update with a deny privilege for an ACE that already contains
     * a grant privilege
     */
    @Test
    public void testMergeAceForUserDenyPrivilegeAfterGrantPrivilege() throws IOException, JsonException {
        testUserId = createTestUser();
        testFolderUrl = createTestFolder();

        // update the ACE
        List<NameValuePair> postParams = new AcePostParamsBuilder(testUserId)
                .withPrivilege(PrivilegeConstants.JCR_WRITE, PrivilegeValues.ALLOW)
                .build();
        addOrUpdateAce(testFolderUrl, postParams);

        JsonObject aceObject = getAce(testFolderUrl, testUserId);
        assertNotNull(aceObject);

        assertEquals(testUserId, aceObject.getString("principal"));

        JsonObject privilegesObject = aceObject.getJsonObject("privileges");
        assertNotNull(privilegesObject);
        assertEquals(1, privilegesObject.size());
        //allow privileges
        assertPrivilege(privilegesObject, true, PrivilegeValues.ALLOW, PrivilegeConstants.JCR_WRITE);


        //2. post a new set of privileges to merge with the existing privileges
        List<NameValuePair> postParams2 = new AcePostParamsBuilder(testUserId)
                //jcr:write is not posted, so it should remain in the granted ACE

                //deny the jcr:nodeTypeManagement privilege, which should merge with the
                //existing ACE.
                .withPrivilege(PrivilegeConstants.JCR_NODE_TYPE_MANAGEMENT, PrivilegeValues.DENY)
                .build();
        addOrUpdateAce(testFolderUrl, postParams2);

        JsonObject aceObject2 = getAce(testFolderUrl, testUserId);
        assertNotNull(aceObject);

        assertEquals(testUserId, aceObject2.getString("principal"));

        JsonObject privilegesObject2 = aceObject2.getJsonObject("privileges");
        assertNotNull(privilegesObject2);
        assertEquals(2, privilegesObject2.size());
        //allow privileges
        assertPrivilege(privilegesObject2, true, PrivilegeValues.ALLOW, PrivilegeConstants.JCR_WRITE);
        //deny privileges
        assertPrivilege(privilegesObject2, true, PrivilegeValues.DENY, PrivilegeConstants.JCR_NODE_TYPE_MANAGEMENT);
    }

    protected void commonAddAceOrderBy(String order) throws IOException {
        createAceOrderTestFolderWithOneAce();

        testGroupId = createTestGroup();

        addOrUpdateAce(testFolderUrl, testGroupId, true, order);

        JsonObject aclObject = getAcl(testFolderUrl);
        assertNotNull(aclObject);
        assertEquals(2, aclObject.size());

        JsonObject group = aclObject.getJsonObject(testGroupId);
        assertNotNull(group);
        assertEquals(testGroupId, group.getString("principal"));
        assertEquals(1, group.getInt("order"));
        JsonObject user =  aclObject.getJsonObject(testUserId);
        assertNotNull(user);
        assertEquals(testUserId, user.getString("principal"));
        assertEquals(0, user.getInt("order"));
    }

    /**
     * Test to verify adding an ACE before an existing ACE
     * the ACL
     */
    @Test
    public void testAddAceOrderByEmpty() throws IOException, JsonException {
        commonAddAceOrderBy("");
    }

    /**
     * Test to verify adding an ACE before an existing ACE
     * the ACL
     */
    @Test
    public void testAddAceOrderByNull() throws IOException, JsonException {
        commonAddAceOrderBy(null);
    }

    /**
     * Test to verify adding an ACE in the first position of
     * the ACL
     */
    @Test
    public void testAddAceOrderByFirst() throws IOException, JsonException {
        createAceOrderTestFolderWithOneAce();

        testGroupId = createTestGroup();

        addOrUpdateAce(testFolderUrl, testGroupId, true, "first");

        JsonObject aclObject = getAcl(testFolderUrl);
        assertNotNull(aclObject);
        assertEquals(2, aclObject.size());

        JsonObject group = aclObject.getJsonObject(testGroupId);
        assertNotNull(group);
        assertEquals(testGroupId, group.getString("principal"));
        assertEquals(0, group.getInt("order"));
        JsonObject user =  aclObject.getJsonObject(testUserId);
        assertNotNull(user);
        assertEquals(testUserId, user.getString("principal"));
        assertEquals(1, user.getInt("order"));
    }

    /**
     * Test to verify adding an ACE at the end
     * the ACL
     */
    @Test
    public void testAddAceOrderByLast() throws IOException, JsonException {
        createAceOrderTestFolderWithOneAce();

        testGroupId = createTestGroup();

        addOrUpdateAce(testFolderUrl, testGroupId, true, "last");

        JsonObject aclObject = getAcl(testFolderUrl);
        assertNotNull(aclObject);
        assertEquals(2, aclObject.size());

        JsonObject user =  aclObject.getJsonObject(testUserId);
        assertNotNull(user);
        assertEquals(testUserId, user.getString("principal"));
        assertEquals(0, user.getInt("order"));
        JsonObject group = aclObject.getJsonObject(testGroupId);
        assertNotNull(group);
        assertEquals(testGroupId, group.getString("principal"));
        assertEquals(1, group.getInt("order"));

    }

    /**
     * Test to verify adding an ACE before an existing ACE
     * the ACL
     */
    @Test
    public void testAddAceOrderByBefore() throws IOException, JsonException {
        createAceOrderTestFolderWithOneAce();

        testGroupId = createTestGroup();

        addOrUpdateAce(testFolderUrl, testGroupId, true, "before " + testUserId);

        JsonObject aclObject = getAcl(testFolderUrl);
        assertNotNull(aclObject);
        assertEquals(2, aclObject.size());

        JsonObject group = aclObject.getJsonObject(testGroupId);
        assertNotNull(group);
        assertEquals(testGroupId, group.getString("principal"));
        assertEquals(0, group.getInt("order"));
        JsonObject user =  aclObject.getJsonObject(testUserId);
        assertNotNull(user);
        assertEquals(testUserId, user.getString("principal"));
        assertEquals(1, user.getInt("order"));
    }

    /**
     * Test to verify adding an ACE after an existing ACE
     * the ACL
     */
    @Test
    public void testAddAceOrderByAfter() throws IOException, JsonException {
        createAceOrderTestFolderWithOneAce();

        testGroupId = createTestGroup();

        addOrUpdateAce(testFolderUrl, testGroupId, true, "after " + testUserId);

        JsonObject aclObject = getAcl(testFolderUrl);
        assertNotNull(aclObject);
        assertEquals(2, aclObject.size());

        JsonObject user =  aclObject.getJsonObject(testUserId);
        assertNotNull(user);
        assertEquals(testUserId, user.getString("principal"));
        assertEquals(0, user.getInt("order"));
        JsonObject group = aclObject.getJsonObject(testGroupId);
        assertNotNull(group);
        assertEquals(testGroupId, group.getString("principal"));
        assertEquals(1, group.getInt("order"));
    }

    /**
     * Test to verify adding an ACE at a specific index inside
     * the ACL
     */
    @Test
    public void testAddAceOrderByNumeric() throws IOException, JsonException {
        createAceOrderTestFolderWithOneAce();

        testGroupId = createTestGroup();
        addOrUpdateAce(testFolderUrl, testGroupId, true, "0");

        JsonObject aclObject = getAcl(testFolderUrl);
        assertNotNull(aclObject);
        assertEquals(2, aclObject.size());

        JsonObject group = aclObject.getJsonObject(testGroupId);
        assertNotNull(group);
        assertEquals(testGroupId, group.getString("principal"));
        assertEquals(0, group.getInt("order"));

        JsonObject user =  aclObject.getJsonObject(testUserId);
        assertNotNull(user);
        assertEquals(testUserId, user.getString("principal"));
        assertEquals(1, user.getInt("order"));


        //add another principal between the testGroupId and testUserId
        testUserId2 = createTestUser();
        addOrUpdateAce(testFolderUrl, testUserId2, true, "1");

        JsonObject aclObject2 = getAcl(testFolderUrl);
        assertNotNull(aclObject2);
        assertEquals(3, aclObject2.size());

        JsonObject group2 = aclObject2.getJsonObject(testGroupId);
        assertNotNull(group2);
        assertEquals(testGroupId, group2.getString("principal"));
        assertEquals(0, group2.getInt("order"));

        JsonObject user3 =  aclObject2.getJsonObject(testUserId2);
        assertNotNull(user3);
        assertEquals(testUserId2, user3.getString("principal"));
        assertEquals(1, user3.getInt("order"));

        JsonObject user2 =  aclObject2.getJsonObject(testUserId);
        assertNotNull(user2);
        assertEquals(testUserId, user2.getString("principal"));
        assertEquals(2, user2.getInt("order"));
    }

    /**
     * Test to make sure modifying an existing ace without changing the order
     * leaves the ACE in the same position in the ACL
     */
    @Test
    public void testUpdateAcePreservePosition() throws IOException, JsonException {
        createAceOrderTestFolderWithOneAce();

        testGroupId = createTestGroup();

        addOrUpdateAce(testFolderUrl, testGroupId, true, "first");

        //update the ace to make sure the update does not change the ACE order
        addOrUpdateAce(testFolderUrl, testGroupId, false, null);

        JsonObject aclObject = getAcl(testFolderUrl);
        assertNotNull(aclObject);
        assertEquals(2, aclObject.size());

        JsonObject group = aclObject.getJsonObject(testGroupId);
        assertNotNull(group);
        assertEquals(testGroupId, group.getString("principal"));
        assertEquals(0, group.getInt("order"));
        JsonObject user =  aclObject.getJsonObject(testUserId);
        assertNotNull(user);
        assertEquals(testUserId, user.getString("principal"));
        assertEquals(1, user.getInt("order"));
    }


    /**
     * Helper to create a test folder with a single ACE pre-created
     */
    private void createAceOrderTestFolderWithOneAce() throws IOException, JsonException {
        testUserId = createTestUser();

        testFolderUrl = createTestFolder();

        addOrUpdateAce(testFolderUrl, testUserId, true, null);

        JsonObject aclObject = getAcl(testFolderUrl);
        assertNotNull(aclObject);
        assertEquals(1, aclObject.size());

        JsonObject user = aclObject.getJsonObject(testUserId);
        assertNotNull(user);
        assertEquals(testUserId, user.getString("principal"));
        assertEquals(0, user.getInt("order"));
    }

    /**
     * Helper to add or update an ace for testing
     */
    private void addOrUpdateAce(String folderUrl, String principalId, boolean readGranted, String order) throws IOException, JsonException {
        addOrUpdateAce(folderUrl, principalId, readGranted, order, null);
    }
    private void addOrUpdateAce(String folderUrl, String principalId, boolean readGranted, String order, Map<String, Object> restrictions) throws IOException, JsonException {
        //1. create an initial set of privileges
        // update the ACE
        AcePostParamsBuilder builder = new AcePostParamsBuilder(principalId)
                .withPrivilege(PrivilegeConstants.JCR_READ, readGranted ? PrivilegeValues.ALLOW : PrivilegeValues.DENY)
                .withPrivilege(PrivilegeConstants.JCR_WRITE, PrivilegeValues.DENY);
        if (order != null) {
            builder.withOrder(order);
        }
        if (restrictions != null) {
            Set<Entry<String, Object>> entrySet = restrictions.entrySet();
            for (Entry<String, Object> entry : entrySet) {
                Object value = entry.getValue();
                if (value != null) {
                    String rname = entry.getKey();

                    if (value.getClass().isArray()) {
                        int length = Array.getLength(value);
                        String [] rvalues = new String[length];
                        for (int i=0; i < length; i++) {
                            Object rvalue = Array.get(value, i);
                            if (rvalue instanceof String) {
                                rvalues[i] = (String)rvalue;
                            }
                        }
                        builder.withRestriction(rname, rvalues);
                    } else if (value instanceof String) {
                        builder.withRestriction(rname, (String)value);
                    }
                }
            }
        }
        addOrUpdateAce(folderUrl, builder.build());
    }

    /**
     * Test for SLING-1677
     */
    @Test
    public void testModifyAceResponseAsJSON() throws IOException, JsonException {
        testUserId = createTestUser();

        testFolderUrl = createTestFolder();

        // update the ACE
        List<NameValuePair> postParams = new AcePostParamsBuilder(testUserId)
                .withPrivilege(PrivilegeConstants.JCR_READ, PrivilegeValues.ALLOW)
                .withPrivilege(PrivilegeConstants.JCR_WRITE, PrivilegeValues.DENY)
                .withPrivilege(PrivilegeConstants.JCR_MODIFY_ACCESS_CONTROL, PrivilegeValues.BOGUS) //invalid value should be ignored.
                .build();
        String json = addOrUpdateAce(testFolderUrl, postParams, CONTENT_TYPE_JSON);

        //make sure the json response can be parsed as a JSON object
        JsonObject jsonObject = parseJson(json);
        assertNotNull(jsonObject);
    }


    /**
     * Test for SLING-3010
     */
    @Test
    public void testMergeAceForUserGrantNestedAggregatePrivilegeAfterDenySuperAggregatePrivilege() throws IOException, JsonException {
        testUserId = createTestUser();

        testFolderUrl = createTestFolder();

        // update the ACE
        List<NameValuePair> postParams = new AcePostParamsBuilder(testUserId)
                .withPrivilege(PrivilegeConstants.JCR_VERSION_MANAGEMENT, PrivilegeValues.DENY)
                .withPrivilege(PrivilegeConstants.JCR_READ, PrivilegeValues.DENY)
                .withPrivilege(PrivilegeConstants.JCR_MODIFY_ACCESS_CONTROL, PrivilegeValues.DENY)
                .withPrivilege(PrivilegeConstants.REP_WRITE, PrivilegeValues.DENY)
                .build();
        addOrUpdateAce(testFolderUrl, postParams);

        //2. now grant the jcr:write subset from the rep:write aggregate privilege
        postParams = new AcePostParamsBuilder(testUserId)
                .withPrivilege(PrivilegeConstants.JCR_VERSION_MANAGEMENT, PrivilegeValues.ALLOW)
                .withPrivilege(PrivilegeConstants.JCR_READ, PrivilegeValues.ALLOW)
                .withPrivilege(PrivilegeConstants.JCR_MODIFY_ACCESS_CONTROL, PrivilegeValues.ALLOW)
                .withPrivilege(PrivilegeConstants.JCR_WRITE, PrivilegeValues.ALLOW) //sub-aggregate of rep:write
                .build();
        addOrUpdateAce(testFolderUrl, postParams);

        //3. verify that the acl has the correct values
        JsonObject aceObject = getAce(testFolderUrl, testUserId);
        assertNotNull(aceObject);

        assertEquals(testUserId, aceObject.getString("principal"));

        JsonObject privilegesObject = aceObject.getJsonObject("privileges");
        assertNotNull(privilegesObject);
        assertEquals(5, privilegesObject.size());
        //allow privileges
        assertPrivilege(privilegesObject, true, PrivilegeValues.ALLOW, PrivilegeConstants.JCR_VERSION_MANAGEMENT);
        assertPrivilege(privilegesObject, true, PrivilegeValues.ALLOW, PrivilegeConstants.JCR_READ);
        assertPrivilege(privilegesObject, true, PrivilegeValues.ALLOW, PrivilegeConstants.JCR_MODIFY_ACCESS_CONTROL);
        assertPrivilege(privilegesObject, true, PrivilegeValues.ALLOW, PrivilegeConstants.JCR_WRITE);
        //deny privileges
        assertPrivilege(privilegesObject, true, PrivilegeValues.DENY, PrivilegeConstants.JCR_NODE_TYPE_MANAGEMENT);
    }

    /**
     * Test for SLING-3010
     */
    @Test
    public void testMergeAceForUserGrantAggregatePrivilegePartsAfterDenyAggregatePrivilege() throws IOException, JsonException {
        testUserId = createTestUser();

        testFolderUrl = createTestFolder();

        // update the ACE
        List<NameValuePair> postParams = new AcePostParamsBuilder(testUserId)
                .withPrivilege(PrivilegeConstants.JCR_VERSION_MANAGEMENT, PrivilegeValues.DENY)
                .withPrivilege(PrivilegeConstants.JCR_READ, PrivilegeValues.DENY)
                .withPrivilege(PrivilegeConstants.JCR_MODIFY_ACCESS_CONTROL, PrivilegeValues.DENY)
                .withPrivilege(PrivilegeConstants.REP_WRITE, PrivilegeValues.DENY)
                .build();
        addOrUpdateAce(testFolderUrl, postParams);

        //2. now grant the all the privileges contained in the rep:write privilege
        postParams = new AcePostParamsBuilder(testUserId)
                .withPrivilege(PrivilegeConstants.JCR_VERSION_MANAGEMENT, PrivilegeValues.ALLOW)
                .withPrivilege(PrivilegeConstants.JCR_READ, PrivilegeValues.ALLOW)
                .withPrivilege(PrivilegeConstants.JCR_MODIFY_ACCESS_CONTROL, PrivilegeValues.ALLOW)
                .withPrivilege(PrivilegeConstants.JCR_NODE_TYPE_MANAGEMENT, PrivilegeValues.ALLOW)
                .withPrivilege(PrivilegeConstants.JCR_WRITE, PrivilegeValues.ALLOW) //sub-aggregate of rep:write
                .build();
        addOrUpdateAce(testFolderUrl, postParams);

        //3. verify that the acl has the correct values
        JsonObject aceObject = getAce(testFolderUrl, testUserId);
        assertNotNull(aceObject);

        assertEquals(testUserId, aceObject.getString("principal"));

        JsonObject privilegesObject = aceObject.getJsonObject("privileges");
        assertNotNull(privilegesObject);
        assertEquals(4, privilegesObject.size());
        //allow privileges
        assertPrivilege(privilegesObject, true, PrivilegeValues.ALLOW, PrivilegeConstants.JCR_VERSION_MANAGEMENT);
        assertPrivilege(privilegesObject, true, PrivilegeValues.ALLOW, PrivilegeConstants.JCR_READ);
        assertPrivilege(privilegesObject, true, PrivilegeValues.ALLOW, PrivilegeConstants.JCR_MODIFY_ACCESS_CONTROL);
        assertPrivilege(privilegesObject, true, PrivilegeValues.ALLOW, PrivilegeConstants.REP_WRITE);
    }

    /**
     * SLING-8117 - Test to verify adding an ACE with restriction to
     * the ACL
     */
    @Test
    public void testAddAceWithRestriction() throws IOException, JsonException {
        createAceOrderTestFolderWithOneAce();

        testGroupId = createTestGroup();

        // update the ACE
        List<NameValuePair> postParams = new AcePostParamsBuilder(testGroupId)
                .withOrder("first")
                .withPrivilege(PrivilegeConstants.JCR_READ, PrivilegeValues.ALLOW)
                .withPrivilege(PrivilegeConstants.JCR_WRITE, PrivilegeValues.DENY)
                .withRestriction(AccessControlConstants.REP_GLOB, "/hello")
                .withRestriction(AccessControlConstants.REP_ITEM_NAMES, new String[] {"child1", "child2"})
                .build();
        addOrUpdateAce(testFolderUrl, postParams);

        JsonObject aclObject = getAcl(testFolderUrl);
        assertNotNull(aclObject);
        assertEquals(2, aclObject.size());

        JsonObject group = aclObject.getJsonObject(testGroupId);
        assertNotNull(group);
        assertEquals(testGroupId, group.getString("principal"));
        assertEquals(0, group.getInt("order"));

        JsonObject groupPrivilegesObject = group.getJsonObject("privileges");
        assertNotNull(groupPrivilegesObject);
        assertEquals(2, groupPrivilegesObject.size());

        VerifyAce verifyRestrictions = jsonValue -> {
            assertNotNull(jsonValue);
            assertTrue(jsonValue instanceof JsonObject);
            JsonObject restrictionsObj = (JsonObject)jsonValue;

            JsonValue repGlobValue = restrictionsObj.get(AccessControlConstants.REP_GLOB);
            assertNotNull(repGlobValue);
            assertTrue(repGlobValue instanceof JsonString);
            assertEquals("/hello", ((JsonString)repGlobValue).getString());

            JsonValue repItemNamesValue = restrictionsObj.get(AccessControlConstants.REP_ITEM_NAMES);
            assertNotNull(repItemNamesValue);
            assertTrue(repItemNamesValue instanceof JsonArray);
            assertEquals(2, ((JsonArray)repItemNamesValue).size());
        };
        //allow privilege
        assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.ALLOW, PrivilegeConstants.JCR_READ, verifyRestrictions);
        //deny privilege
        assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.DENY, PrivilegeConstants.JCR_WRITE, verifyRestrictions);

        JsonObject user =  aclObject.getJsonObject(testUserId);
        assertNotNull(user);
        assertEquals(testUserId, user.getString("principal"));
        assertEquals(1, user.getInt("order"));
        JsonObject userPrivilegesObject = user.getJsonObject("privileges");
        assertNotNull(userPrivilegesObject);
        assertEquals(2, userPrivilegesObject.size());
        VerifyAce verifyRestrictions2 = jsonValue -> {
            assertNotNull(jsonValue);
            assertEquals(ValueType.TRUE, jsonValue.getValueType());
        };
        //allow privilege
        assertPrivilege(userPrivilegesObject, true, PrivilegeValues.ALLOW, PrivilegeConstants.JCR_READ, verifyRestrictions2);
        //deny privilege
        assertPrivilege(userPrivilegesObject, true, PrivilegeValues.DENY, PrivilegeConstants.JCR_WRITE, verifyRestrictions2);
    }

    /**
     * SLING-8117 - Test to verify merging an ACE with an existing restriction to
     * the ACL
     */
    @Test
    public void testUpdateAceToMergeNewRestriction() throws IOException, JsonException {
        createAceOrderTestFolderWithOneAce();

        testGroupId = createTestGroup();

        //first create an ACE with the first restriction
        List<NameValuePair> postParams = new AcePostParamsBuilder(testGroupId)
                .withOrder("first")
                .withPrivilege(PrivilegeConstants.JCR_READ, PrivilegeValues.ALLOW)
                .withPrivilege(PrivilegeConstants.JCR_WRITE, PrivilegeValues.DENY)
                .withRestriction(AccessControlConstants.REP_GLOB, "/hello")
                .build();
        addOrUpdateAce(testFolderUrl, postParams);

        JsonObject aclObject = getAcl(testFolderUrl);
        assertNotNull(aclObject);
        assertEquals(2, aclObject.size());

        JsonObject group = aclObject.getJsonObject(testGroupId);
        assertNotNull(group);
        assertEquals(testGroupId, group.getString("principal"));
        assertEquals(0, group.getInt("order"));

        //verify restrictions are returned
        JsonObject privilegesObject = group.getJsonObject("privileges");
        assertNotNull(privilegesObject);
        assertEquals(2, privilegesObject.size());
        VerifyAce verifyRestrictions = jsonValue -> {
            assertNotNull(jsonValue);
            assertTrue(jsonValue instanceof JsonObject);
            JsonObject restrictionsObj = (JsonObject)jsonValue;

            JsonValue repGlobValue = restrictionsObj.get(AccessControlConstants.REP_GLOB);
            assertNotNull(repGlobValue);
            assertTrue(repGlobValue instanceof JsonString);
            assertEquals("/hello", ((JsonString)repGlobValue).getString());
        };
        assertPrivilege(privilegesObject, true, PrivilegeValues.ALLOW, PrivilegeConstants.JCR_READ, verifyRestrictions);
        assertPrivilege(privilegesObject, true, PrivilegeValues.DENY, PrivilegeConstants.JCR_WRITE, verifyRestrictions);



        //second update the ACE with a second restriction
        List<NameValuePair> postParams2 = new AcePostParamsBuilder(testGroupId)
                .withOrder("first")
                .withPrivilege(PrivilegeConstants.JCR_READ, PrivilegeValues.ALLOW)
                .withPrivilege(PrivilegeConstants.JCR_WRITE, PrivilegeValues.DENY)
                .withRestriction(AccessControlConstants.REP_ITEM_NAMES, new String[] {"child1", "child2"})
                .build();
        addOrUpdateAce(testFolderUrl, postParams2);

        JsonObject aclObject2 = getAcl(testFolderUrl);
        assertNotNull(aclObject2);
        assertEquals(2, aclObject2.size());

        JsonObject group2 = aclObject2.getJsonObject(testGroupId);
        assertNotNull(group2);
        assertEquals(testGroupId, group2.getString("principal"));
        assertEquals(0, group2.getInt("order"));

        //verify restrictions are returned
        JsonObject privilegesObject2 = group2.getJsonObject("privileges");
        assertNotNull(privilegesObject2);
        assertEquals(2, privilegesObject2.size());
        VerifyAce verifyRestrictions2 = jsonValue -> {
            assertNotNull(jsonValue);
            assertTrue(jsonValue instanceof JsonObject);
            JsonObject restrictionsObj = (JsonObject)jsonValue;

            JsonValue repGlobValue = restrictionsObj.get(AccessControlConstants.REP_GLOB);
            assertNotNull(repGlobValue);
            assertTrue(repGlobValue instanceof JsonString);
            assertEquals("/hello", ((JsonString)repGlobValue).getString());

            JsonValue repItemNamesValue = restrictionsObj.get(AccessControlConstants.REP_ITEM_NAMES);
            assertNotNull(repItemNamesValue);
            assertTrue(repItemNamesValue instanceof JsonArray);
            assertEquals(2, ((JsonArray)repItemNamesValue).size());
        };
        assertPrivilege(privilegesObject2, true, PrivilegeValues.ALLOW, PrivilegeConstants.JCR_READ, verifyRestrictions2);
        assertPrivilege(privilegesObject2, true, PrivilegeValues.DENY, PrivilegeConstants.JCR_WRITE, verifyRestrictions2);
    }

    /**
     * SLING-8117 - Test to verify removing a restriction from an ACE
     */
    @Test
    public void testUpdateAceToRemoveRestriction() throws IOException, JsonException {
        createAceOrderTestFolderWithOneAce();

        testGroupId = createTestGroup();

        //first create an ACE with the first restriction
        List<NameValuePair> postParams = new AcePostParamsBuilder(testGroupId)
                .withOrder("first")
                .withPrivilege(PrivilegeConstants.JCR_READ, PrivilegeValues.ALLOW)
                .withPrivilege(PrivilegeConstants.JCR_WRITE, PrivilegeValues.DENY)
                .withRestriction(AccessControlConstants.REP_GLOB, "/hello")
                .withRestriction(AccessControlConstants.REP_ITEM_NAMES, new String[] {"child1", "child2"})
                .build();
        addOrUpdateAce(testFolderUrl, postParams);

        JsonObject aclObject = getAcl(testFolderUrl);
        assertNotNull(aclObject);
        assertEquals(2, aclObject.size());

        JsonObject group = aclObject.getJsonObject(testGroupId);
        assertNotNull(group);
        assertEquals(testGroupId, group.getString("principal"));
        assertEquals(0, group.getInt("order"));

        //verify restrictions are returned
        JsonObject privilegesObject = group.getJsonObject("privileges");
        assertNotNull(privilegesObject);
        assertEquals(2, privilegesObject.size());
        VerifyAce verifyRestrictions = jsonValue -> {
            assertNotNull(jsonValue);
            assertTrue(jsonValue instanceof JsonObject);
            JsonObject restrictionsObj = (JsonObject)jsonValue;

            JsonValue repGlobValue = restrictionsObj.get(AccessControlConstants.REP_GLOB);
            assertNotNull(repGlobValue);
            assertTrue(repGlobValue instanceof JsonString);
            assertEquals("/hello", ((JsonString)repGlobValue).getString());

            JsonValue repItemNamesValue = restrictionsObj.get(AccessControlConstants.REP_ITEM_NAMES);
            assertNotNull(repItemNamesValue);
            assertTrue(repItemNamesValue instanceof JsonArray);
            assertEquals(2, ((JsonArray)repItemNamesValue).size());
        };
        assertPrivilege(privilegesObject, true, PrivilegeValues.ALLOW, PrivilegeConstants.JCR_READ, verifyRestrictions);
        assertPrivilege(privilegesObject, true, PrivilegeValues.DENY, PrivilegeConstants.JCR_WRITE, verifyRestrictions);


        //second remove the restrictions
        List<NameValuePair> postParams2 = new AcePostParamsBuilder(testGroupId)
                .withOrder("first")
                .withDeleteRestriction(AccessControlConstants.REP_GLOB, DeleteValues.TRUE)
                .withDeleteRestriction(AccessControlConstants.REP_ITEM_NAMES, DeleteValues.VALUE_DOES_NOT)
                .withDeleteRestriction(AccessControlConstants.REP_ITEM_NAMES, DeleteValues.MATTER)
                .build();
        addOrUpdateAce(testFolderUrl, postParams2);

        JsonObject aclObject2 = getAcl(testFolderUrl);
        assertNotNull(aclObject2);
        assertEquals(2, aclObject2.size());

        JsonObject group2 = aclObject2.getJsonObject(testGroupId);
        assertNotNull(group2);
        assertEquals(testGroupId, group2.getString("principal"));
        assertEquals(0, group2.getInt("order"));

        //verify no restrictions are returned
        JsonObject privilegesObject2 = group2.getJsonObject("privileges");
        assertNotNull(privilegesObject2);
        assertEquals(2, privilegesObject2.size());
        VerifyAce verifyRestrictions2 = jsonValue -> {
            assertNotNull(jsonValue);
            assertEquals(ValueType.TRUE, jsonValue.getValueType());
        };
        assertPrivilege(privilegesObject2, true, PrivilegeValues.ALLOW, PrivilegeConstants.JCR_READ, verifyRestrictions2);
        assertPrivilege(privilegesObject2, true, PrivilegeValues.DENY, PrivilegeConstants.JCR_WRITE, verifyRestrictions2);
    }

    /**
     * SLING-8117 - Test to verify removing a restriction from an ACE does not happen
     * if a new value with the same name has also been supplied
     */
    @Test
    public void testUpdateAceToRemoveRestrictionWithConflict() throws IOException, JsonException {
        createAceOrderTestFolderWithOneAce();

        testGroupId = createTestGroup();

        //first create an ACE with the first restriction
        List<NameValuePair> postParams = new AcePostParamsBuilder(testGroupId)
                .withOrder("first")
                .withPrivilege(PrivilegeConstants.JCR_READ, PrivilegeValues.ALLOW)
                .withPrivilege(PrivilegeConstants.JCR_WRITE, PrivilegeValues.DENY)
                .withRestriction(AccessControlConstants.REP_GLOB, "/hello")
                .build();
        addOrUpdateAce(testFolderUrl, postParams);

        JsonObject aclObject = getAcl(testFolderUrl);
        assertNotNull(aclObject);
        assertEquals(2, aclObject.size());

        JsonObject group = aclObject.getJsonObject(testGroupId);
        assertNotNull(group);
        assertEquals(testGroupId, group.getString("principal"));
        assertEquals(0, group.getInt("order"));

        //verify restrictions are returned
        JsonObject privilegesObject = group.getJsonObject("privileges");
        assertNotNull(privilegesObject);
        assertEquals(2, privilegesObject.size());
        VerifyAce verifyRestrictions = jsonValue -> {
            assertNotNull(jsonValue);
            assertTrue(jsonValue instanceof JsonObject);
            JsonObject restrictionsObj = (JsonObject)jsonValue;

            JsonValue repGlobValue = restrictionsObj.get(AccessControlConstants.REP_GLOB);
            assertNotNull(repGlobValue);
            assertTrue(repGlobValue instanceof JsonString);
            assertEquals("/hello", ((JsonString)repGlobValue).getString());
        };
        assertPrivilege(privilegesObject, true, PrivilegeValues.ALLOW, PrivilegeConstants.JCR_READ, verifyRestrictions);
        assertPrivilege(privilegesObject, true, PrivilegeValues.DENY, PrivilegeConstants.JCR_WRITE, verifyRestrictions);


        //second remove the restriction and also supply a new value of the same
        List<NameValuePair> postParams2 = new AcePostParamsBuilder(testGroupId)
                .withOrder("first")
                .withDeleteRestriction(AccessControlConstants.REP_GLOB, DeleteValues.TRUE)
                .withRestriction(AccessControlConstants.REP_GLOB, "/hello_again")
                .build();
        addOrUpdateAce(testFolderUrl, postParams2);

        JsonObject aclObject2 = getAcl(testFolderUrl);
        assertNotNull(aclObject2);
        assertEquals(2, aclObject2.size());

        JsonObject group2 = aclObject2.getJsonObject(testGroupId);
        assertNotNull(group2);
        assertEquals(testGroupId, group2.getString("principal"));
        assertEquals(0, group2.getInt("order"));

        //verify restrictions are returned
        JsonObject privilegesObject2 = group2.getJsonObject("privileges");
        assertNotNull(privilegesObject2);
        assertEquals(2, privilegesObject2.size());
        VerifyAce verifyRestrictions2 = jsonValue -> {
            assertNotNull(jsonValue);
            assertTrue(jsonValue instanceof JsonObject);
            JsonObject restrictionsObj = (JsonObject)jsonValue;

            JsonValue repGlobValue = restrictionsObj.get(AccessControlConstants.REP_GLOB);
            assertNotNull(repGlobValue);
            assertTrue(repGlobValue instanceof JsonString);
            assertEquals("/hello_again", ((JsonString)repGlobValue).getString());
        };
        assertPrivilege(privilegesObject2, true, PrivilegeValues.ALLOW, PrivilegeConstants.JCR_READ, verifyRestrictions2);
        assertPrivilege(privilegesObject2, true, PrivilegeValues.DENY, PrivilegeConstants.JCR_WRITE, verifyRestrictions2);
    }

    /**
     * SLING-8809 - Test to verify submitting an invalid principalId returns a
     * good error message instead of a NullPointerException
     */
    @Test
    public void testModifyAceForInvalidUser() throws IOException, JsonException {
        String invalidUserId = "notRealUser123";

        testFolderUrl = createTestFolder();

        // update the ACE
        List<NameValuePair> postParams = new AcePostParamsBuilder(invalidUserId)
                .with(":http-equiv-accept", JSONResponse.RESPONSE_CONTENT_TYPE)
                .withPrivilege(PrivilegeConstants.JCR_READ, PrivilegeValues.ALLOW)
                .withPrivilege(PrivilegeConstants.JCR_WRITE, PrivilegeValues.DENY)
                .withPrivilege(PrivilegeConstants.JCR_MODIFY_ACCESS_CONTROL, PrivilegeValues.BOGUS)//invalid value should be ignored.
                .build();
        String json = addOrUpdateAce(testFolderUrl, postParams, CONTENT_TYPE_JSON, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        assertNotNull(json);

        JsonObject jsonObject = parseJson(json);
        assertEquals("javax.jcr.RepositoryException: Invalid principalId was submitted.", jsonObject.getString("status.message"));
    }

    /**
     * SLING-8811 - Test to verify that the "changes" list of a modifyAce response
     * returns the list of principals that were changed
     */
    @Test
    public void testModifyAceChangesInResponse() throws IOException, JsonException {
        testUserId = createTestUser();

        testFolderUrl = createTestFolder();

        // update the ACE
        List<NameValuePair> postParams = new AcePostParamsBuilder(testUserId)
                .with(":http-equiv-accept", JSONResponse.RESPONSE_CONTENT_TYPE)
                .withPrivilege(PrivilegeConstants.JCR_READ, PrivilegeValues.ALLOW)
                .withPrivilege(PrivilegeConstants.JCR_WRITE, PrivilegeValues.DENY)
                .withPrivilege(PrivilegeConstants.JCR_MODIFY_ACCESS_CONTROL, PrivilegeValues.BOGUS)//invalid value should be ignored.
                .build();
        String json = addOrUpdateAce(testFolderUrl, postParams, CONTENT_TYPE_JSON);
        assertNotNull(json);

        JsonObject jsonObject = parseJson(json);
        JsonArray changesArray = jsonObject.getJsonArray("changes");
        assertNotNull(changesArray);
        assertEquals(1, changesArray.size());
        JsonObject change = changesArray.getJsonObject(0);
        assertEquals("modified", change.getString("type"));
        assertEquals(testUserId, change.getString("argument"));
    }

    private void testModifyAceRedirect(String redirectTo, int expectedStatus) throws IOException {
        testUserId = createTestUser();

        testFolderUrl = createTestFolder();

        // update the ACE
        List<NameValuePair> postParams = new AcePostParamsBuilder(testUserId)
                .withPrivilege(PrivilegeConstants.JCR_READ, PrivilegeValues.ALLOW)
                .withRedirect(redirectTo)
                .build();
        addOrUpdateAce(testFolderUrl, postParams, expectedStatus);
    }

    @Test
    public void testModifyAceValidRedirect() throws IOException, JsonException {
        testModifyAceRedirect("/*.html", HttpServletResponse.SC_MOVED_TEMPORARILY);
    }

    @Test
    public void testModifyAceInvalidRedirectWithAuthority() throws IOException, JsonException {
        testModifyAceRedirect("https://sling.apache.org", SC_UNPROCESSABLE_ENTITY);
    }

    @Test
    public void testModifyAceInvalidRedirectWithInvalidURI() throws IOException, JsonException {
        testModifyAceRedirect("https://", SC_UNPROCESSABLE_ENTITY);
    }


    /**
     * SLING-11243 - Test to verify adding an ACE with privilege restriction
     */
    @Test
    public void testModifyAceAddAllowPrivilegeRestriction() throws IOException, JsonException {
        testFolderUrl = createTestFolder();
        testGroupId = createTestGroup();

        // update the ACE
        List<NameValuePair> postParams = new AcePostParamsBuilder(testGroupId)
            .withPrivilegeRestriction(PrivilegeValues.ALLOW, PrivilegeConstants.JCR_READ, AccessControlConstants.REP_GLOB, "/hello")
            .withPrivilegeRestriction(PrivilegeValues.ALLOW, PrivilegeConstants.JCR_WRITE, AccessControlConstants.REP_ITEM_NAMES, new String[] {"child1", "child2"})
            .build();
        addOrUpdateAce(testFolderUrl, postParams);
        JsonObject groupPrivilegesObject = getAcePrivleges(testFolderUrl, testGroupId);
        assertEquals(2, groupPrivilegesObject.size());

        //allow privilege
        assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.ALLOW, PrivilegeConstants.JCR_READ, jsonValue -> {
            assertNotNull(jsonValue);
            assertTrue(jsonValue instanceof JsonObject);
            JsonObject restrictionsObj = (JsonObject)jsonValue;

            JsonValue repGlobValue = restrictionsObj.get(AccessControlConstants.REP_GLOB);
            assertNotNull(repGlobValue);
            assertTrue(repGlobValue instanceof JsonString);
            assertEquals("/hello", ((JsonString)repGlobValue).getString());

            JsonValue repItemNamesValue = restrictionsObj.get(AccessControlConstants.REP_ITEM_NAMES);
            assertNull(repItemNamesValue);
        });
        assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.ALLOW, PrivilegeConstants.JCR_WRITE, jsonValue -> {
            assertNotNull(jsonValue);
            assertTrue(jsonValue instanceof JsonObject);
            JsonObject restrictionsObj = (JsonObject)jsonValue;

            JsonValue repGlobValue = restrictionsObj.get(AccessControlConstants.REP_GLOB);
            assertNull(repGlobValue);

            JsonValue repItemNamesValue = restrictionsObj.get(AccessControlConstants.REP_ITEM_NAMES);
            assertNotNull(repItemNamesValue);
            assertTrue(repItemNamesValue instanceof JsonArray);
            assertEquals(2, ((JsonArray)repItemNamesValue).size());
        });
        //deny privilege
        assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.DENY, PrivilegeConstants.JCR_READ, false, null);
        assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.DENY, PrivilegeConstants.JCR_WRITE, false, null);
    }

    /**
     * SLING-11243 - Test to verify adding an ACE with privilege restriction
     */
    @Test
    public void testModifyAceAddDenyPrivilegeRestriction() throws IOException, JsonException {
        testFolderUrl = createTestFolder();
        testGroupId = createTestGroup();

        // update the ACE
        List<NameValuePair> postParams = new AcePostParamsBuilder(testGroupId)
            .withPrivilegeRestriction(PrivilegeValues.DENY, PrivilegeConstants.JCR_READ, AccessControlConstants.REP_GLOB, "/hello")
            .withPrivilegeRestriction(PrivilegeValues.DENY, PrivilegeConstants.JCR_WRITE, AccessControlConstants.REP_ITEM_NAMES, new String[] {"child1", "child2"})
            .build();
        addOrUpdateAce(testFolderUrl, postParams);
        JsonObject groupPrivilegesObject = getAcePrivleges(testFolderUrl, testGroupId);
        assertEquals(2, groupPrivilegesObject.size());

        //allow privilege
        assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.ALLOW, PrivilegeConstants.JCR_READ, false, null);
        assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.ALLOW, PrivilegeConstants.JCR_WRITE, false, null);
        //deny privilege
        assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.DENY, PrivilegeConstants.JCR_READ, jsonValue -> {
            assertNotNull(jsonValue);
            assertTrue(jsonValue instanceof JsonObject);
            JsonObject restrictionsObj = (JsonObject)jsonValue;

            JsonValue repGlobValue = restrictionsObj.get(AccessControlConstants.REP_GLOB);
            assertNotNull(repGlobValue);
            assertTrue(repGlobValue instanceof JsonString);
            assertEquals("/hello", ((JsonString)repGlobValue).getString());

            JsonValue repItemNamesValue = restrictionsObj.get(AccessControlConstants.REP_ITEM_NAMES);
            assertNull(repItemNamesValue);
        });
        assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.DENY, PrivilegeConstants.JCR_WRITE, jsonValue -> {
            assertNotNull(jsonValue);
            assertTrue(jsonValue instanceof JsonObject);
            JsonObject restrictionsObj = (JsonObject)jsonValue;

            JsonValue repGlobValue = restrictionsObj.get(AccessControlConstants.REP_GLOB);
            assertNull(repGlobValue);

            JsonValue repItemNamesValue = restrictionsObj.get(AccessControlConstants.REP_ITEM_NAMES);
            assertNotNull(repItemNamesValue);
            assertTrue(repItemNamesValue instanceof JsonArray);
            assertEquals(2, ((JsonArray)repItemNamesValue).size());
        });
    }

    /**
     * SLING-11243 - Test to verify adding an ACE with privilege restriction
     */
    @Test
    public void testModifyAceAddAllowAndDenyPrivilegeRestriction() throws IOException, JsonException {
        testFolderUrl = createTestFolder();
        testGroupId = createTestGroup();

        // update the ACE
        List<NameValuePair> postParams = new AcePostParamsBuilder(testGroupId)
            .withPrivilegeRestriction(PrivilegeValues.ALLOW, PrivilegeConstants.JCR_READ, AccessControlConstants.REP_GLOB, "/hello")
            .withPrivilegeRestriction(PrivilegeValues.DENY, PrivilegeConstants.JCR_WRITE, AccessControlConstants.REP_ITEM_NAMES, new String[] {"child1", "child2"})
            .build();
        addOrUpdateAce(testFolderUrl, postParams);
        JsonObject groupPrivilegesObject = getAcePrivleges(testFolderUrl, testGroupId);
        assertEquals(2, groupPrivilegesObject.size());

        //allow privilege
        assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.ALLOW, PrivilegeConstants.JCR_READ, jsonValue -> {
            assertNotNull(jsonValue);
            assertTrue(jsonValue instanceof JsonObject);
            JsonObject restrictionsObj = (JsonObject)jsonValue;

            JsonValue repGlobValue = restrictionsObj.get(AccessControlConstants.REP_GLOB);
            assertNotNull(repGlobValue);
            assertTrue(repGlobValue instanceof JsonString);
            assertEquals("/hello", ((JsonString)repGlobValue).getString());

            JsonValue repItemNamesValue = restrictionsObj.get(AccessControlConstants.REP_ITEM_NAMES);
            assertNull(repItemNamesValue);
        });
        assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.ALLOW, PrivilegeConstants.JCR_WRITE, false, null);
        //deny privilege
        assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.DENY, PrivilegeConstants.JCR_WRITE, jsonValue -> {
            assertNotNull(jsonValue);
            assertTrue(jsonValue instanceof JsonObject);
            JsonObject restrictionsObj = (JsonObject)jsonValue;

            JsonValue repGlobValue = restrictionsObj.get(AccessControlConstants.REP_GLOB);
            assertNull(repGlobValue);

            JsonValue repItemNamesValue = restrictionsObj.get(AccessControlConstants.REP_ITEM_NAMES);
            assertNotNull(repItemNamesValue);
            assertTrue(repItemNamesValue instanceof JsonArray);
            assertEquals(2, ((JsonArray)repItemNamesValue).size());
        });
        assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.DENY, PrivilegeConstants.JCR_READ, false, null);
    }

    /**
     * SLING-11243 - Test to verify modifying an ACE to remove a privilege
     */
    @Test
    public void testModifyAceDeleteWriteAllowPrivilege() throws IOException, JsonException {
        commonModifyAceDeleteWritePrivilege();

        // remove just the allow privilege and leave the deny privilege active
        List<NameValuePair> postParams2 = new AcePostParamsBuilder(testGroupId)
                .withDeletePrivilege(PrivilegeConstants.JCR_WRITE, DeleteValues.ALLOW)
                .build();
        addOrUpdateAce(testFolderUrl, postParams2);
        JsonObject groupPrivilegesObject2 = getAcePrivleges(testFolderUrl, testGroupId);
        assertEquals(2, groupPrivilegesObject2.size());

        //allow privilege
        assertPrivilege(groupPrivilegesObject2, true, PrivilegeValues.ALLOW, PrivilegeConstants.JCR_WRITE, false, null);
        //deny privilege
        assertPrivilege(groupPrivilegesObject2, true, PrivilegeValues.DENY, PrivilegeConstants.JCR_WRITE, true, jsonValue -> {
            assertNotNull(jsonValue);
            assertTrue(jsonValue instanceof JsonObject);
            JsonObject restrictionsObj = (JsonObject)jsonValue;

            JsonValue repItemNamesValue = restrictionsObj.get(AccessControlConstants.REP_ITEM_NAMES);
            assertNotNull(repItemNamesValue);
            assertTrue(repItemNamesValue instanceof JsonArray);
            assertEquals(2, ((JsonArray)repItemNamesValue).size());
        });
    }

    /**
     * SLING-11243 - Test to verify modifying an ACE to remove a privilege
     */
    @Test
    public void testModifyAceDeleteWritePrivilegeWithInvalidValue() throws IOException, JsonException {
        commonModifyAceDeleteWritePrivilege();

        // remove with an invalid value
        List<NameValuePair> postParams2 = new AcePostParamsBuilder(testGroupId)
                .withDeletePrivilege(PrivilegeConstants.JCR_WRITE, DeleteValues.VALUE_DOES_NOT)
                .build();
        addOrUpdateAce(testFolderUrl, postParams2);
        JsonObject groupPrivilegesObject2 = getAcePrivleges(testFolderUrl, testGroupId);
        assertEquals(2, groupPrivilegesObject2.size());

        //allow privilege should remain as before
        assertPrivilege(groupPrivilegesObject2, true, PrivilegeValues.DENY, PrivilegeConstants.JCR_WRITE, true, jsonValue -> {
            assertNotNull(jsonValue);
            assertTrue(jsonValue instanceof JsonObject);
            JsonObject restrictionsObj = (JsonObject)jsonValue;

            JsonValue repItemNamesValue = restrictionsObj.get(AccessControlConstants.REP_ITEM_NAMES);
            assertNotNull(repItemNamesValue);
            assertTrue(repItemNamesValue instanceof JsonArray);
            assertEquals(2, ((JsonArray)repItemNamesValue).size());
        });
        //deny privilege should remain as before
        assertPrivilege(groupPrivilegesObject2, true, PrivilegeValues.ALLOW, PrivilegeConstants.JCR_WRITE, true, jsonValue -> {
            assertNotNull(jsonValue);
            assertTrue(jsonValue instanceof JsonObject);
            JsonObject restrictionsObj = (JsonObject)jsonValue;

            JsonValue repGlobValue = restrictionsObj.get(AccessControlConstants.REP_GLOB);
            assertNotNull(repGlobValue);
            assertTrue(repGlobValue instanceof JsonString);
            assertEquals("/hello", ((JsonString)repGlobValue).getString());
        });
    }

    /**
     * SLING-11243 - Test to verify modifying an ACE to remove a privilege
     */
    @Test
    public void testModifyAceDeleteWriteDenyPrivilege() throws IOException, JsonException {
        commonModifyAceDeleteWritePrivilege();

        // remove just the deny privilege and leave the allow privilege active
        List<NameValuePair> postParams2 = new AcePostParamsBuilder(testGroupId)
                .withDeletePrivilege(PrivilegeConstants.JCR_WRITE, DeleteValues.DENY)
                .build();
        addOrUpdateAce(testFolderUrl, postParams2);
        JsonObject groupPrivilegesObject2 = getAcePrivleges(testFolderUrl, testGroupId);
        assertEquals(2, groupPrivilegesObject2.size());

        //allow privilege
        assertPrivilege(groupPrivilegesObject2, true, PrivilegeValues.ALLOW, PrivilegeConstants.JCR_WRITE, true, jsonValue -> {
            assertNotNull(jsonValue);
            assertTrue(jsonValue instanceof JsonObject);
            JsonObject restrictionsObj = (JsonObject)jsonValue;

            JsonValue repGlobValue = restrictionsObj.get(AccessControlConstants.REP_GLOB);
            assertNotNull(repGlobValue);
            assertTrue(repGlobValue instanceof JsonString);
            assertEquals("/hello", ((JsonString)repGlobValue).getString());
        });
        //deny privilege
        assertPrivilege(groupPrivilegesObject2, true, PrivilegeValues.DENY, PrivilegeConstants.JCR_WRITE, false , null);
    }

    /**
     * SLING-11243 - Test to verify modifying an ACE to remove a privilege
     */
    @Test
    public void testModifyAceDeleteWriteAllPrivilege() throws IOException, JsonException {
        commonModifyAceDeleteWritePrivilege();

        // remove both the allow and the deny privilege
        List<NameValuePair> postParams2 = new AcePostParamsBuilder(testGroupId)
                .withDeletePrivilege(PrivilegeConstants.JCR_WRITE, DeleteValues.ALL)
                .build();
        addOrUpdateAce(testFolderUrl, postParams2);
        JsonObject groupPrivilegesObject2 = getAcePrivleges(testFolderUrl, testGroupId);
        assertEquals(1, groupPrivilegesObject2.size());

        //allow privilege
        assertPrivilege(groupPrivilegesObject2, false, PrivilegeValues.ALLOW, PrivilegeConstants.JCR_WRITE);
        //deny privilege
        assertPrivilege(groupPrivilegesObject2, false, PrivilegeValues.DENY, PrivilegeConstants.JCR_WRITE);
    }

    protected void commonModifyAceDeleteWritePrivilege() throws IOException {
        testFolderUrl = createTestFolder();
        testGroupId = createTestGroup();

        // update the ACE
        List<NameValuePair> postParams = new AcePostParamsBuilder(testGroupId)
            .withPrivilege(PrivilegeConstants.JCR_READ, PrivilegeValues.ALLOW)
            .withPrivilegeRestriction(PrivilegeValues.ALLOW, PrivilegeConstants.JCR_WRITE, AccessControlConstants.REP_GLOB, "/hello")
            .withPrivilegeRestriction(PrivilegeValues.DENY, PrivilegeConstants.JCR_WRITE, AccessControlConstants.REP_ITEM_NAMES, new String[] {"child1", "child2"})
            .build();
        addOrUpdateAce(testFolderUrl, postParams);
        JsonObject groupPrivilegesObject = getAcePrivleges(testFolderUrl, testGroupId);
        assertEquals(2, groupPrivilegesObject.size());

        //allow privilege
        assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.DENY, PrivilegeConstants.JCR_WRITE, true, jsonValue -> {
            assertNotNull(jsonValue);
            assertTrue(jsonValue instanceof JsonObject);
            JsonObject restrictionsObj = (JsonObject)jsonValue;

            JsonValue repItemNamesValue = restrictionsObj.get(AccessControlConstants.REP_ITEM_NAMES);
            assertNotNull(repItemNamesValue);
            assertTrue(repItemNamesValue instanceof JsonArray);
            assertEquals(2, ((JsonArray)repItemNamesValue).size());
        });
        //deny privilege
        assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.ALLOW, PrivilegeConstants.JCR_WRITE, true, jsonValue -> {
            assertNotNull(jsonValue);
            assertTrue(jsonValue instanceof JsonObject);
            JsonObject restrictionsObj = (JsonObject)jsonValue;

            JsonValue repGlobValue = restrictionsObj.get(AccessControlConstants.REP_GLOB);
            assertNotNull(repGlobValue);
            assertTrue(repGlobValue instanceof JsonString);
            assertEquals("/hello", ((JsonString)repGlobValue).getString());
        });
    }

    /**
     * SLING-11243 - Test to verify an allow and deny restriction with the same value are both submitted.
     * The allow restriction wins out and the deny restriction is ignored
     */
    @Test
    public void testModifyAceAllowWinsOverDenyWithSameRestrictions() throws IOException, JsonException {
        testFolderUrl = createTestFolder();
        testGroupId = createTestGroup();

        // update the ACE
        List<NameValuePair> postParams = new AcePostParamsBuilder(testGroupId)
            .withPrivilegeRestriction(PrivilegeValues.ALLOW, PrivilegeConstants.JCR_WRITE, AccessControlConstants.REP_GLOB, "/hello")
            .withPrivilegeRestriction(PrivilegeValues.DENY, PrivilegeConstants.JCR_WRITE, AccessControlConstants.REP_GLOB, "/hello")
            .build();
        addOrUpdateAce(testFolderUrl, postParams);
        JsonObject groupPrivilegesObject = getAcePrivleges(testFolderUrl, testGroupId);
        assertEquals(1, groupPrivilegesObject.size());

        //allow privilege is there
        assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.ALLOW, PrivilegeConstants.JCR_WRITE, true, jsonValue -> {
            assertNotNull(jsonValue);
            assertTrue(jsonValue instanceof JsonObject);
            JsonObject restrictionsObj = (JsonObject)jsonValue;

            JsonValue repGlobValue = restrictionsObj.get(AccessControlConstants.REP_GLOB);
            assertNotNull(repGlobValue);
            assertTrue(repGlobValue instanceof JsonString);
            assertEquals("/hello", ((JsonString)repGlobValue).getString());
        });
        //deny privilege is not there
        assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.DENY, PrivilegeConstants.JCR_WRITE, false, null);
    }

    /**
     * SLING-11243 - Test to verify adding an ACE with privilege restriction
     */
    @Test
    public void testModifyAceDeleteAllowPrivilegeRestriction() throws IOException, JsonException {
        testModifyAceAddAllowAndDenyPrivilegeRestriction();

        // update the ACE
        List<NameValuePair> postParams = new AcePostParamsBuilder(testGroupId)
                .withDeletePrivilegeRestriction(PrivilegeConstants.JCR_READ, AccessControlConstants.REP_GLOB, DeleteValues.ALLOW)
                .withDeletePrivilegeRestriction(PrivilegeConstants.JCR_WRITE, AccessControlConstants.REP_ITEM_NAMES, DeleteValues.ALLOW)
            .build();
        addOrUpdateAce(testFolderUrl, postParams);
        JsonObject groupPrivilegesObject = getAcePrivleges(testFolderUrl, testGroupId);
        assertEquals(2, groupPrivilegesObject.size());

        //allow privilege
        assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.ALLOW, PrivilegeConstants.JCR_READ, true, jsonValue -> assertEquals(ValueType.TRUE, jsonValue.getValueType()));
        assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.ALLOW, PrivilegeConstants.JCR_WRITE, false, null);
        //deny privilege
        assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.DENY, PrivilegeConstants.JCR_READ, false, null);
        assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.DENY, PrivilegeConstants.JCR_WRITE, true, jsonValue -> {
            assertNotNull(jsonValue);
            assertTrue(jsonValue instanceof JsonObject);
            JsonObject restrictionsObj = (JsonObject)jsonValue;

            JsonValue repGlobValue = restrictionsObj.get(AccessControlConstants.REP_GLOB);
            assertNull(repGlobValue);

            JsonValue repItemNamesValue = restrictionsObj.get(AccessControlConstants.REP_ITEM_NAMES);
            assertNotNull(repItemNamesValue);
            assertTrue(repItemNamesValue instanceof JsonArray);
            assertEquals(2, ((JsonArray)repItemNamesValue).size());
        });
    }

    /**
     * SLING-11243 - Test to verify adding an ACE with privilege restriction
     */
    @Test
    public void testModifyAceDeletePrivilegeRestrictionWithInvalidValue() throws IOException, JsonException {
        testModifyAceAddAllowAndDenyPrivilegeRestriction();

        // update the ACE
        List<NameValuePair> postParams = new AcePostParamsBuilder(testGroupId)
                .withDeletePrivilegeRestriction(PrivilegeConstants.JCR_READ, AccessControlConstants.REP_GLOB, DeleteValues.VALUE_DOES_NOT)
                .withDeletePrivilegeRestriction(PrivilegeConstants.JCR_WRITE, AccessControlConstants.REP_ITEM_NAMES, DeleteValues.MATTER)
            .build();
        addOrUpdateAce(testFolderUrl, postParams);
        JsonObject groupPrivilegesObject = getAcePrivleges(testFolderUrl, testGroupId);
        assertEquals(2, groupPrivilegesObject.size());

        //allow privilege remain as before
        assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.ALLOW, PrivilegeConstants.JCR_READ, jsonValue -> {
            assertNotNull(jsonValue);
            assertTrue(jsonValue instanceof JsonObject);
            JsonObject restrictionsObj = (JsonObject)jsonValue;

            JsonValue repGlobValue = restrictionsObj.get(AccessControlConstants.REP_GLOB);
            assertNotNull(repGlobValue);
            assertTrue(repGlobValue instanceof JsonString);
            assertEquals("/hello", ((JsonString)repGlobValue).getString());

            JsonValue repItemNamesValue = restrictionsObj.get(AccessControlConstants.REP_ITEM_NAMES);
            assertNull(repItemNamesValue);
        });
        assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.ALLOW, PrivilegeConstants.JCR_WRITE, false, null);
        //deny privilege remain as before
        assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.DENY, PrivilegeConstants.JCR_WRITE, jsonValue -> {
            assertNotNull(jsonValue);
            assertTrue(jsonValue instanceof JsonObject);
            JsonObject restrictionsObj = (JsonObject)jsonValue;

            JsonValue repGlobValue = restrictionsObj.get(AccessControlConstants.REP_GLOB);
            assertNull(repGlobValue);

            JsonValue repItemNamesValue = restrictionsObj.get(AccessControlConstants.REP_ITEM_NAMES);
            assertNotNull(repItemNamesValue);
            assertTrue(repItemNamesValue instanceof JsonArray);
            assertEquals(2, ((JsonArray)repItemNamesValue).size());
        });
        assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.DENY, PrivilegeConstants.JCR_READ, false, null);
    }

    /**
     * SLING-11243 - Test to verify adding an ACE with privilege restriction
     */
    @Test
    public void testModifyAceDeleteDenyPrivilegeRestriction() throws IOException, JsonException {
        testModifyAceAddAllowAndDenyPrivilegeRestriction();

        // update the ACE
        List<NameValuePair> postParams = new AcePostParamsBuilder(testGroupId)
            .withDeletePrivilegeRestriction(PrivilegeConstants.JCR_READ, AccessControlConstants.REP_GLOB, DeleteValues.DENY)
            .withDeletePrivilegeRestriction(PrivilegeConstants.JCR_WRITE, AccessControlConstants.REP_ITEM_NAMES, DeleteValues.DENY)
            .build();
        addOrUpdateAce(testFolderUrl, postParams);
        JsonObject groupPrivilegesObject = getAcePrivleges(testFolderUrl, testGroupId);
        assertEquals(2, groupPrivilegesObject.size());

        //allow privilege
        assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.ALLOW, PrivilegeConstants.JCR_READ, true, jsonValue -> {
            assertNotNull(jsonValue);
            assertTrue(jsonValue instanceof JsonObject);
            JsonObject restrictionsObj = (JsonObject)jsonValue;

            JsonValue repGlobValue = restrictionsObj.get(AccessControlConstants.REP_GLOB);
            assertNotNull(repGlobValue);
            assertTrue(repGlobValue instanceof JsonString);
            assertEquals("/hello", ((JsonString)repGlobValue).getString());

            JsonValue repItemNamesValue = restrictionsObj.get(AccessControlConstants.REP_ITEM_NAMES);
            assertNull(repItemNamesValue);
        });
        assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.ALLOW, PrivilegeConstants.JCR_WRITE, false, null);
        //deny privilege
        assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.DENY, PrivilegeConstants.JCR_READ, false, null);
        assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.DENY, PrivilegeConstants.JCR_WRITE, true, jsonValue -> assertEquals(ValueType.TRUE, jsonValue.getValueType()));
    }

    /**
     * SLING-11243 - Test to verify adding an ACE with privilege restriction
     */
    @Test
    public void testModifyAceDeleteAllowAndDenyPrivilegeRestriction() throws IOException, JsonException {
        testModifyAceAddAllowAndDenyPrivilegeRestriction();

        // update the ACE
        List<NameValuePair> postParams = new AcePostParamsBuilder(testGroupId)
            .withDeletePrivilegeRestriction(PrivilegeConstants.JCR_READ, AccessControlConstants.REP_GLOB, DeleteValues.ALL)
            .withDeletePrivilegeRestriction(PrivilegeConstants.JCR_WRITE, AccessControlConstants.REP_ITEM_NAMES, DeleteValues.ALL)
            .build();
        addOrUpdateAce(testFolderUrl, postParams);
        JsonObject groupPrivilegesObject = getAcePrivleges(testFolderUrl, testGroupId);
        assertEquals(2, groupPrivilegesObject.size());

        //allow privilege
        assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.ALLOW, PrivilegeConstants.JCR_READ, true, jsonValue -> assertEquals(ValueType.TRUE, jsonValue.getValueType()));
        assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.ALLOW, PrivilegeConstants.JCR_WRITE, false, null);
        //deny privilege
        assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.DENY, PrivilegeConstants.JCR_READ, false, null);
        assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.DENY, PrivilegeConstants.JCR_WRITE, true, jsonValue -> assertEquals(ValueType.TRUE, jsonValue.getValueType()));
    }

    /**
     * SLING-11243 - Test to verify adding an ACE with privilege restriction
     */
    @Test
    public void testModifyAceAddPrivilegeRestrictionOnAggregateLeaf() throws IOException, JsonException {
        testFolderUrl = createTestFolder();
        testGroupId = createTestGroup();

        // update the ACE
        List<NameValuePair> postParams = new AcePostParamsBuilder(testGroupId)
                .withPrivilege(PrivilegeConstants.JCR_READ, PrivilegeValues.ALLOW)
                .withPrivilegeRestriction(PrivilegeValues.ALLOW, PrivilegeConstants.REP_READ_PROPERTIES, AccessControlConstants.REP_GLOB, "/hello")
                .withPrivilegeRestriction(PrivilegeValues.ALLOW, PrivilegeConstants.REP_READ_NODES, AccessControlConstants.REP_GLOB, "/hello2")
                .build();
        addOrUpdateAce(testFolderUrl, postParams);

        JsonObject groupPrivilegesObject = getAcePrivleges(testFolderUrl, testGroupId);
        assertEquals(2, groupPrivilegesObject.size());

        //allow privilege
        assertPrivilege(groupPrivilegesObject, false, PrivilegeValues.ALLOW, PrivilegeConstants.JCR_READ);
        assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.ALLOW, PrivilegeConstants.REP_READ_PROPERTIES, true, jsonValue -> {
            assertNotNull(jsonValue);
            assertTrue(jsonValue instanceof JsonObject);
            JsonObject restrictionsObj = (JsonObject)jsonValue;

            JsonValue repGlobValue = restrictionsObj.get(AccessControlConstants.REP_GLOB);
            assertNotNull(repGlobValue);
            assertTrue(repGlobValue instanceof JsonString);
            assertEquals("/hello", ((JsonString)repGlobValue).getString());
        });
        assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.ALLOW, PrivilegeConstants.REP_READ_NODES, true, jsonValue -> {
            assertNotNull(jsonValue);
            assertTrue(jsonValue instanceof JsonObject);
            JsonObject restrictionsObj = (JsonObject)jsonValue;

            JsonValue repGlobValue = restrictionsObj.get(AccessControlConstants.REP_GLOB);
            assertNotNull(repGlobValue);
            assertTrue(repGlobValue instanceof JsonString);
            assertEquals("/hello2", ((JsonString)repGlobValue).getString());
        });
    }

    /**
     * SLING-11243 - Test to verify adding an ACE with privilege restriction
     */
    @Test
    public void testModifyAceAddSamePrivilegeRestrictionOnAllAggregateLeafs() throws IOException, JsonException {
        testFolderUrl = createTestFolder();
        testGroupId = createTestGroup();

        // update the ACE
        List<NameValuePair> postParams = new AcePostParamsBuilder(testGroupId)
                .withPrivilege(PrivilegeConstants.JCR_READ, PrivilegeValues.ALLOW)
                .withPrivilegeRestriction(PrivilegeValues.ALLOW, PrivilegeConstants.REP_READ_PROPERTIES, AccessControlConstants.REP_GLOB, "/hello")
                .withPrivilegeRestriction(PrivilegeValues.ALLOW, PrivilegeConstants.REP_READ_NODES, AccessControlConstants.REP_GLOB, "/hello")
                .build();
        addOrUpdateAce(testFolderUrl, postParams);

        JsonObject groupPrivilegesObject = getAcePrivleges(testFolderUrl, testGroupId);
        assertEquals(1, groupPrivilegesObject.size());

        //allow privilege
        assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.ALLOW, PrivilegeConstants.JCR_READ, true, jsonValue -> {
            assertNotNull(jsonValue);
            assertTrue(jsonValue instanceof JsonObject);
            JsonObject restrictionsObj = (JsonObject)jsonValue;

            JsonValue repGlobValue = restrictionsObj.get(AccessControlConstants.REP_GLOB);
            assertNotNull(repGlobValue);
            assertTrue(repGlobValue instanceof JsonString);
            assertEquals("/hello", ((JsonString)repGlobValue).getString());
        });
    }


    /**
     * SLING-11243 - Test to verify adding an ACE with privilege restriction
     */
    @Test
    public void testModifyAceAllowAllDenyReadAccessControl() throws IOException, JsonException {
        testFolderUrl = createTestFolder();
        testGroupId = createTestGroup();

        // update the ACE
        List<NameValuePair> postParams = new AcePostParamsBuilder(testGroupId)
                .withPrivilege(PrivilegeConstants.JCR_ALL, PrivilegeValues.ALLOW)
                .withPrivilege(PrivilegeConstants.JCR_READ_ACCESS_CONTROL, PrivilegeValues.DENY)
                .build();
        addOrUpdateAce(testFolderUrl, postParams);

        JsonObject groupPrivilegesObject = getAcePrivleges(testFolderUrl, testGroupId);
        assertEquals(14, groupPrivilegesObject.size());

        //allow privilege
        assertPrivilege(groupPrivilegesObject, false, PrivilegeValues.ALLOW, PrivilegeConstants.JCR_ALL);
        Stream.of(PrivilegeConstants.JCR_VERSION_MANAGEMENT,
                PrivilegeConstants.JCR_LIFECYCLE_MANAGEMENT,
                PrivilegeConstants.JCR_RETENTION_MANAGEMENT,
                PrivilegeConstants.REP_INDEX_DEFINITION_MANAGEMENT,
                PrivilegeConstants.REP_PRIVILEGE_MANAGEMENT,
                PrivilegeConstants.JCR_WORKSPACE_MANAGEMENT,
                PrivilegeConstants.JCR_MODIFY_ACCESS_CONTROL,
                PrivilegeConstants.JCR_NAMESPACE_MANAGEMENT,
                PrivilegeConstants.REP_USER_MANAGEMENT,
                PrivilegeConstants.REP_WRITE,
                PrivilegeConstants.JCR_LOCK_MANAGEMENT,
                PrivilegeConstants.JCR_NODE_TYPE_DEFINITION_MANAGEMENT,
                PrivilegeConstants.JCR_READ
                )
            .forEach(p -> assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.ALLOW, p, true, verifyTrue));

        //deny privilege
        assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.DENY, PrivilegeConstants.JCR_READ_ACCESS_CONTROL, true, verifyTrue);
    }

    /**
     * SLING-11243 - Test to verify adding an ACE with privilege restriction
     */
    @Test
    public void testModifyAceDenyAllAllowReadAccessControl() throws IOException, JsonException {
        testFolderUrl = createTestFolder();
        testGroupId = createTestGroup();

        // update the ACE
        List<NameValuePair> postParams = new AcePostParamsBuilder(testGroupId)
                .withPrivilege(PrivilegeConstants.JCR_ALL, PrivilegeValues.DENY)
                .withPrivilege(PrivilegeConstants.JCR_READ_ACCESS_CONTROL, PrivilegeValues.ALLOW)
                .build();
        addOrUpdateAce(testFolderUrl, postParams);

        JsonObject groupPrivilegesObject = getAcePrivleges(testFolderUrl, testGroupId);
        assertEquals(14, groupPrivilegesObject.size());

        //allow privilege
        assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.ALLOW, PrivilegeConstants.JCR_READ_ACCESS_CONTROL, true, verifyTrue);

        //deny privilege
        assertPrivilege(groupPrivilegesObject, false, PrivilegeValues.DENY, PrivilegeConstants.JCR_ALL);
        Stream.of(PrivilegeConstants.JCR_VERSION_MANAGEMENT,
                PrivilegeConstants.JCR_LIFECYCLE_MANAGEMENT,
                PrivilegeConstants.JCR_RETENTION_MANAGEMENT,
                PrivilegeConstants.REP_INDEX_DEFINITION_MANAGEMENT,
                PrivilegeConstants.REP_PRIVILEGE_MANAGEMENT,
                PrivilegeConstants.JCR_WORKSPACE_MANAGEMENT,
                PrivilegeConstants.JCR_MODIFY_ACCESS_CONTROL,
                PrivilegeConstants.JCR_NAMESPACE_MANAGEMENT,
                PrivilegeConstants.REP_USER_MANAGEMENT,
                PrivilegeConstants.REP_WRITE,
                PrivilegeConstants.JCR_LOCK_MANAGEMENT,
                PrivilegeConstants.JCR_NODE_TYPE_DEFINITION_MANAGEMENT,
                PrivilegeConstants.JCR_READ
                )
            .forEach(p -> assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.DENY, p, true, verifyTrue));
    }

    /**
     * SLING-11243 - Test to verify adding an ACE with privilege restriction
     */
    @Test
    public void testModifyAceDenyAllAllowReadProperties() throws IOException, JsonException {
        testFolderUrl = createTestFolder();
        testGroupId = createTestGroup();

        // update the ACE
        List<NameValuePair> postParams = new AcePostParamsBuilder(testGroupId)
                .withPrivilege(PrivilegeConstants.JCR_ALL, PrivilegeValues.DENY)
                .withPrivilege(PrivilegeConstants.REP_READ_PROPERTIES, PrivilegeValues.ALLOW)
                .build();
        addOrUpdateAce(testFolderUrl, postParams);

        JsonObject groupPrivilegesObject = getAcePrivleges(testFolderUrl, testGroupId);
        assertEquals(15, groupPrivilegesObject.size());

        //allow privilege
        assertPrivilege(groupPrivilegesObject, false, PrivilegeValues.DENY, PrivilegeConstants.JCR_ALL);
        assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.ALLOW, PrivilegeConstants.REP_READ_PROPERTIES, true, verifyTrue);

        //deny privilege
        assertPrivilege(groupPrivilegesObject, false, PrivilegeValues.DENY, PrivilegeConstants.JCR_ALL);
        Stream.of(PrivilegeConstants.JCR_VERSION_MANAGEMENT,
                PrivilegeConstants.JCR_READ_ACCESS_CONTROL,
                PrivilegeConstants.JCR_LIFECYCLE_MANAGEMENT,
                PrivilegeConstants.JCR_RETENTION_MANAGEMENT,
                PrivilegeConstants.REP_INDEX_DEFINITION_MANAGEMENT,
                PrivilegeConstants.REP_PRIVILEGE_MANAGEMENT,
                PrivilegeConstants.JCR_WORKSPACE_MANAGEMENT,
                PrivilegeConstants.JCR_MODIFY_ACCESS_CONTROL,
                PrivilegeConstants.JCR_NAMESPACE_MANAGEMENT,
                PrivilegeConstants.REP_USER_MANAGEMENT,
                PrivilegeConstants.REP_WRITE,
                PrivilegeConstants.JCR_LOCK_MANAGEMENT,
                PrivilegeConstants.JCR_NODE_TYPE_DEFINITION_MANAGEMENT,
                PrivilegeConstants.REP_READ_NODES
                )
            .forEach(p -> assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.DENY, p, true, verifyTrue));
    }

    /**
     * SLING-11243 - Test to verify adding an ACE with privilege restriction
     */
    @Test
    public void testModifyAceDenyAllGrantLeafsOfRepWrite() throws IOException, JsonException {
        testFolderUrl = createTestFolder();
        testGroupId = createTestGroup();

        // update the ACE
        List<NameValuePair> postParams = new AcePostParamsBuilder(testGroupId)
                .withPrivilege(PrivilegeConstants.JCR_ALL, PrivilegeValues.DENY)
                .withPrivilege(PrivilegeConstants.JCR_REMOVE_CHILD_NODES, PrivilegeValues.ALLOW)
                .withPrivilege(PrivilegeConstants.JCR_REMOVE_NODE, PrivilegeValues.ALLOW)
                .withPrivilege(PrivilegeConstants.JCR_ADD_CHILD_NODES, PrivilegeValues.ALLOW)
                .withPrivilege(PrivilegeConstants.JCR_NODE_TYPE_MANAGEMENT, PrivilegeValues.ALLOW)
                .withPrivilege(PrivilegeConstants.REP_ADD_PROPERTIES, PrivilegeValues.ALLOW)
                .withPrivilege(PrivilegeConstants.REP_REMOVE_PROPERTIES, PrivilegeValues.ALLOW)
                .withPrivilege(PrivilegeConstants.REP_ALTER_PROPERTIES, PrivilegeValues.ALLOW)
                .build();
        addOrUpdateAce(testFolderUrl, postParams);

        JsonObject groupPrivilegesObject = getAcePrivleges(testFolderUrl, testGroupId);
        assertEquals(14, groupPrivilegesObject.size());

        assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.ALLOW, PrivilegeConstants.REP_WRITE, true, verifyTrue);

        //deny privilege
        Stream.of(PrivilegeConstants.REP_INDEX_DEFINITION_MANAGEMENT,
                PrivilegeConstants.REP_PRIVILEGE_MANAGEMENT,
                PrivilegeConstants.JCR_WORKSPACE_MANAGEMENT,
                PrivilegeConstants.JCR_MODIFY_ACCESS_CONTROL,
                PrivilegeConstants.JCR_NAMESPACE_MANAGEMENT,
                PrivilegeConstants.JCR_VERSION_MANAGEMENT,
                PrivilegeConstants.JCR_READ_ACCESS_CONTROL,
                PrivilegeConstants.JCR_NODE_TYPE_DEFINITION_MANAGEMENT,
                PrivilegeConstants.JCR_LIFECYCLE_MANAGEMENT,
                PrivilegeConstants.JCR_RETENTION_MANAGEMENT,
                PrivilegeConstants.JCR_LOCK_MANAGEMENT,
                PrivilegeConstants.REP_USER_MANAGEMENT,
                PrivilegeConstants.JCR_READ)
            .forEach(p -> assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.DENY, p, true, verifyTrue));
    }

    /**
     * SLING-11243 - Test to verify adding an ACE with privilege restriction
     */
    @Test
    public void testModifyAceDenyAllGrantModifyPropertiesAndOtherLeafsOfRepWrite() throws IOException, JsonException {
        testFolderUrl = createTestFolder();
        testGroupId = createTestGroup();

        // update the ACE
        List<NameValuePair> postParams = new AcePostParamsBuilder(testGroupId)
                .withPrivilege(PrivilegeConstants.JCR_ALL, PrivilegeValues.DENY)
                .withPrivilege(PrivilegeConstants.JCR_REMOVE_CHILD_NODES, PrivilegeValues.ALLOW)
                .withPrivilege(PrivilegeConstants.JCR_REMOVE_NODE, PrivilegeValues.ALLOW)
                .withPrivilege(PrivilegeConstants.JCR_ADD_CHILD_NODES, PrivilegeValues.ALLOW)
                .withPrivilege(PrivilegeConstants.JCR_NODE_TYPE_MANAGEMENT, PrivilegeValues.ALLOW)
                .withPrivilege(PrivilegeConstants.JCR_MODIFY_PROPERTIES, PrivilegeValues.ALLOW)
                .build();
        addOrUpdateAce(testFolderUrl, postParams);

        JsonObject groupPrivilegesObject = getAcePrivleges(testFolderUrl, testGroupId);
        assertEquals(14, groupPrivilegesObject.size());

        //allow privilege
        assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.ALLOW, PrivilegeConstants.REP_WRITE, true, verifyTrue);

        //deny privilege
        Stream.of(PrivilegeConstants.REP_INDEX_DEFINITION_MANAGEMENT,
                PrivilegeConstants.REP_PRIVILEGE_MANAGEMENT,
                PrivilegeConstants.JCR_WORKSPACE_MANAGEMENT,
                PrivilegeConstants.JCR_MODIFY_ACCESS_CONTROL,
                PrivilegeConstants.JCR_NAMESPACE_MANAGEMENT,
                PrivilegeConstants.JCR_VERSION_MANAGEMENT,
                PrivilegeConstants.JCR_READ_ACCESS_CONTROL,
                PrivilegeConstants.JCR_NODE_TYPE_DEFINITION_MANAGEMENT,
                PrivilegeConstants.JCR_LIFECYCLE_MANAGEMENT,
                PrivilegeConstants.JCR_RETENTION_MANAGEMENT,
                PrivilegeConstants.JCR_LOCK_MANAGEMENT,
                PrivilegeConstants.REP_USER_MANAGEMENT,
                PrivilegeConstants.JCR_READ)
            .forEach(p -> assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.DENY, p, true, verifyTrue));
    }

    /**
     * SLING-11243 - Test to verify adding an ACE with privilege restriction
     */
    @Test
    public void testModifyAceDenyAllGrantJcrWriteAndOtherLeafsOfRepWrite() throws IOException, JsonException {
        testFolderUrl = createTestFolder();
        testGroupId = createTestGroup();

        // update the ACE
        List<NameValuePair> postParams = new AcePostParamsBuilder(testGroupId)
                .withPrivilege(PrivilegeConstants.JCR_ALL, PrivilegeValues.DENY)
                .withPrivilege(PrivilegeConstants.JCR_WRITE, PrivilegeValues.ALLOW)
                .withPrivilege(PrivilegeConstants.JCR_NODE_TYPE_MANAGEMENT, PrivilegeValues.ALLOW)
                .withPrivilege(PrivilegeConstants.JCR_MODIFY_PROPERTIES, PrivilegeValues.ALLOW)
                .build();
        addOrUpdateAce(testFolderUrl, postParams);

        JsonObject groupPrivilegesObject = getAcePrivleges(testFolderUrl, testGroupId);
        assertEquals(14, groupPrivilegesObject.size());

        //allow privilege
        assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.ALLOW, PrivilegeConstants.REP_WRITE, true, verifyTrue);

        //deny privilege
        Stream.of(PrivilegeConstants.REP_INDEX_DEFINITION_MANAGEMENT,
                PrivilegeConstants.REP_PRIVILEGE_MANAGEMENT,
                PrivilegeConstants.JCR_WORKSPACE_MANAGEMENT,
                PrivilegeConstants.JCR_MODIFY_ACCESS_CONTROL,
                PrivilegeConstants.JCR_NAMESPACE_MANAGEMENT,
                PrivilegeConstants.JCR_VERSION_MANAGEMENT,
                PrivilegeConstants.JCR_READ_ACCESS_CONTROL,
                PrivilegeConstants.JCR_NODE_TYPE_DEFINITION_MANAGEMENT,
                PrivilegeConstants.JCR_LIFECYCLE_MANAGEMENT,
                PrivilegeConstants.JCR_RETENTION_MANAGEMENT,
                PrivilegeConstants.JCR_LOCK_MANAGEMENT,
                PrivilegeConstants.REP_USER_MANAGEMENT,
                PrivilegeConstants.JCR_READ)
            .forEach(p -> assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.DENY, p, true, verifyTrue));
    }

    /**
     * SLING-11243 - Test to verify adding an ACE with privilege restriction
     */
    @Test
    public void testModifyAceAllowAllDenyJcrWriteAndOtherLeafsOfRepWrite() throws IOException, JsonException {
        testFolderUrl = createTestFolder();
        testGroupId = createTestGroup();

        // update the ACE
        List<NameValuePair> postParams = new AcePostParamsBuilder(testGroupId)
                .withPrivilege(PrivilegeConstants.JCR_ALL, PrivilegeValues.ALLOW)
                .withPrivilege(PrivilegeConstants.JCR_WRITE, PrivilegeValues.DENY)
                .withPrivilege(PrivilegeConstants.JCR_NODE_TYPE_MANAGEMENT, PrivilegeValues.DENY)
                .withPrivilege(PrivilegeConstants.JCR_MODIFY_PROPERTIES, PrivilegeValues.DENY)
                .build();
        addOrUpdateAce(testFolderUrl, postParams);

        JsonObject groupPrivilegesObject = getAcePrivleges(testFolderUrl, testGroupId);
        assertEquals(14, groupPrivilegesObject.size());

        //allow privilege
        Stream.of(PrivilegeConstants.REP_INDEX_DEFINITION_MANAGEMENT,
                PrivilegeConstants.REP_PRIVILEGE_MANAGEMENT,
                PrivilegeConstants.JCR_WORKSPACE_MANAGEMENT,
                PrivilegeConstants.JCR_MODIFY_ACCESS_CONTROL,
                PrivilegeConstants.JCR_NAMESPACE_MANAGEMENT,
                PrivilegeConstants.JCR_VERSION_MANAGEMENT,
                PrivilegeConstants.JCR_READ_ACCESS_CONTROL,
                PrivilegeConstants.JCR_NODE_TYPE_DEFINITION_MANAGEMENT,
                PrivilegeConstants.JCR_LIFECYCLE_MANAGEMENT,
                PrivilegeConstants.JCR_RETENTION_MANAGEMENT,
                PrivilegeConstants.JCR_LOCK_MANAGEMENT,
                PrivilegeConstants.REP_USER_MANAGEMENT,
                PrivilegeConstants.JCR_READ)
            .forEach(p -> assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.ALLOW, p, true, verifyTrue));

        //deny privilege
        assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.DENY, PrivilegeConstants.REP_WRITE, true, verifyTrue);
    }

    /**
     * SLING-11243 - Test to verify an allow and deny restriction with the same value are both submitted.
     * The allow restriction wins out and the deny restriction is ignored
     */
    @Test
    public void testModifyAceAllowReadAndDenyRead() throws IOException, JsonException {
        testFolderUrl = createTestFolder();
        testGroupId = createTestGroup();

        // start with an existing restriction
        addOrUpdateAce(testFolderUrl, new AcePostParamsBuilder(testGroupId)
                .withPrivilege(PrivilegeConstants.JCR_READ, PrivilegeValues.ALLOW)
                .build());

        // update the ACE
        List<NameValuePair> postParams = new AcePostParamsBuilder(testGroupId)
            .withPrivilege(PrivilegeConstants.JCR_READ, PrivilegeValues.ALLOW)
            .withPrivilege(PrivilegeConstants.JCR_READ, PrivilegeValues.DENY)
            .build();
        addOrUpdateAce(testFolderUrl, postParams);
        JsonObject groupPrivilegesObject = getAcePrivleges(testFolderUrl, testGroupId);
        assertEquals(1, groupPrivilegesObject.size());

        //allow privilege is there (allow prefererred over deny)
        assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.ALLOW, PrivilegeConstants.JCR_READ, true, verifyTrue);
        //deny privilege is not there
        assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.DENY, PrivilegeConstants.JCR_READ, false, null);
    }

    /**
     * SLING-11243 - Test to verify an allow and deny restriction with the same value are both submitted.
     * The allow restriction wins out and the deny restriction is ignored
     */
    @Test
    public void testModifyAceDenyReadAndAllowRead() throws IOException, JsonException {
        testFolderUrl = createTestFolder();
        testGroupId = createTestGroup();

        // start with an existing restriction
        addOrUpdateAce(testFolderUrl, new AcePostParamsBuilder(testGroupId)
                .withPrivilege(PrivilegeConstants.JCR_READ, PrivilegeValues.ALLOW)
                .build());

        // update the ACE
        List<NameValuePair> postParams = new AcePostParamsBuilder(testGroupId)
            .withPrivilege(PrivilegeConstants.JCR_READ, PrivilegeValues.DENY)
            .withPrivilege(PrivilegeConstants.JCR_READ, PrivilegeValues.ALLOW)
            .build();
        addOrUpdateAce(testFolderUrl, postParams);
        JsonObject groupPrivilegesObject = getAcePrivleges(testFolderUrl, testGroupId);
        assertEquals(1, groupPrivilegesObject.size());

        //allow privilege is there (allow prefererred over deny)
        assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.ALLOW, PrivilegeConstants.JCR_READ, true, verifyTrue);
        //deny privilege is not there
        assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.DENY, PrivilegeConstants.JCR_READ, false, null);
    }

    /**
     * SLING-11243 - Test to verify an allow and deny restriction with the same value are both submitted.
     * The allow restriction wins out and the deny restriction is ignored
     */
    @Test
    public void testModifyAceAllowReadAndNoneRead() throws IOException, JsonException {
        testFolderUrl = createTestFolder();
        testGroupId = createTestGroup();

        // start with an existing restriction
        addOrUpdateAce(testFolderUrl, new AcePostParamsBuilder(testGroupId)
                .withPrivilege(PrivilegeConstants.JCR_READ, PrivilegeValues.ALLOW)
                .build());

        // update the ACE
        List<NameValuePair> postParams = new AcePostParamsBuilder(testGroupId)
            .withPrivilege(PrivilegeConstants.JCR_READ, PrivilegeValues.ALLOW)
            .withPrivilege(PrivilegeConstants.JCR_READ, PrivilegeValues.NONE)
            .build();
        addOrUpdateAce(testFolderUrl, postParams);
        JsonObject groupPrivilegesObject = getAcePrivleges(testFolderUrl, testGroupId);
        assertEquals(1, groupPrivilegesObject.size());

        //allow privilege is there (allow prefererred over none)
        assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.ALLOW, PrivilegeConstants.JCR_READ, true, verifyTrue);
        //deny privilege is not there
        assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.DENY, PrivilegeConstants.JCR_READ, false, null);
    }


    /**
     * SLING-11243 - Test to verify an allow and deny restriction with the same value are both submitted.
     * The allow restriction wins out and the deny restriction is ignored
     */
    @Test
    public void testModifyAceAllowReadRestrictionAndDenySameReadRestriction() throws IOException, JsonException {
        testFolderUrl = createTestFolder();
        testGroupId = createTestGroup();

        // start with an existing restriction
        addOrUpdateAce(testFolderUrl, new AcePostParamsBuilder(testGroupId)
                .withPrivilegeRestriction(PrivilegeValues.ALLOW, PrivilegeConstants.JCR_READ, AccessControlConstants.REP_GLOB, "/hello")
                .build());

        // update the ACE
        List<NameValuePair> postParams = new AcePostParamsBuilder(testGroupId)
            .withPrivilegeRestriction(PrivilegeValues.ALLOW, PrivilegeConstants.JCR_READ, AccessControlConstants.REP_GLOB, "/hello")
            .withPrivilegeRestriction(PrivilegeValues.DENY, PrivilegeConstants.JCR_READ, AccessControlConstants.REP_GLOB, "/hello")
            .build();
        addOrUpdateAce(testFolderUrl, postParams);
        JsonObject groupPrivilegesObject = getAcePrivleges(testFolderUrl, testGroupId);
        assertEquals(1, groupPrivilegesObject.size());

        //allow privilege is there (allow prefererred over deny)
        //allow privilege
        assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.ALLOW, PrivilegeConstants.JCR_READ, true, jsonValue -> {
            assertNotNull(jsonValue);
            assertTrue(jsonValue instanceof JsonObject);
            JsonObject restrictionsObj = (JsonObject)jsonValue;

            JsonValue repGlobValue = restrictionsObj.get(AccessControlConstants.REP_GLOB);
            assertNotNull(repGlobValue);
            assertTrue(repGlobValue instanceof JsonString);
            assertEquals("/hello", ((JsonString)repGlobValue).getString());
        });
        //deny privilege is not there
        assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.DENY, PrivilegeConstants.JCR_READ, false, null);
    }

    /**
     * SLING-11243 - Test to verify an allow and deny restriction with the same value are both submitted.
     * The allow restriction wins out and the deny restriction is ignored
     */
    @Test
    public void testModifyAceDenyReadRestrictionAndAllowSameReadRestriction() throws IOException, JsonException {
        testFolderUrl = createTestFolder();
        testGroupId = createTestGroup();

        // start with an existing restriction
        addOrUpdateAce(testFolderUrl, new AcePostParamsBuilder(testGroupId)
                .withPrivilegeRestriction(PrivilegeValues.ALLOW, PrivilegeConstants.JCR_READ, AccessControlConstants.REP_GLOB, "/hello")
                .build());

        // update the ACE
        List<NameValuePair> postParams = new AcePostParamsBuilder(testGroupId)
            .withPrivilegeRestriction(PrivilegeValues.DENY, PrivilegeConstants.JCR_READ, AccessControlConstants.REP_GLOB, "/hello")
            .withPrivilegeRestriction(PrivilegeValues.ALLOW, PrivilegeConstants.JCR_READ, AccessControlConstants.REP_GLOB, "/hello")
            .build();
        addOrUpdateAce(testFolderUrl, postParams);
        JsonObject groupPrivilegesObject = getAcePrivleges(testFolderUrl, testGroupId);
        assertEquals(1, groupPrivilegesObject.size());

        //allow privilege is there (allow prefererred over deny)
        //allow privilege
        assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.ALLOW, PrivilegeConstants.JCR_READ, true, jsonValue -> {
            assertNotNull(jsonValue);
            assertTrue(jsonValue instanceof JsonObject);
            JsonObject restrictionsObj = (JsonObject)jsonValue;

            JsonValue repGlobValue = restrictionsObj.get(AccessControlConstants.REP_GLOB);
            assertNotNull(repGlobValue);
            assertTrue(repGlobValue instanceof JsonString);
            assertEquals("/hello", ((JsonString)repGlobValue).getString());
        });
        //deny privilege is not there
        assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.DENY, PrivilegeConstants.JCR_READ, false, null);
    }

    /**
     * SLING-11243 - Test to verify an allow and deny restriction with the same value are both submitted.
     * The allow restriction wins out and the deny restriction is ignored
     */
    @Test
    public void testModifyAceAllowReadRestrictionAndNoneSameReadRestriction() throws IOException, JsonException {
        testFolderUrl = createTestFolder();
        testGroupId = createTestGroup();

        // start with an existing restriction
        addOrUpdateAce(testFolderUrl, new AcePostParamsBuilder(testGroupId)
                .withPrivilegeRestriction(PrivilegeValues.ALLOW, PrivilegeConstants.JCR_READ, AccessControlConstants.REP_GLOB, "/hello2")
                .build());

        // update the ACE
        List<NameValuePair> postParams = new AcePostParamsBuilder(testGroupId)
            .withPrivilegeRestriction(PrivilegeValues.ALLOW, PrivilegeConstants.JCR_READ, AccessControlConstants.REP_GLOB, "/hello")
            .withPrivilegeRestriction(PrivilegeValues.DENY, PrivilegeConstants.JCR_READ, AccessControlConstants.REP_GLOB, "/hello")
            .build();
        addOrUpdateAce(testFolderUrl, postParams);
        JsonObject groupPrivilegesObject = getAcePrivleges(testFolderUrl, testGroupId);
        assertEquals(1, groupPrivilegesObject.size());

        //allow privilege is there (allow preferred over deny)
        //allow privilege
        assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.ALLOW, PrivilegeConstants.JCR_READ, true, jsonValue -> {
            assertNotNull(jsonValue);
            assertTrue(jsonValue instanceof JsonObject);
            JsonObject restrictionsObj = (JsonObject)jsonValue;

            JsonValue repGlobValue = restrictionsObj.get(AccessControlConstants.REP_GLOB);
            assertNotNull(repGlobValue);
            assertTrue(repGlobValue instanceof JsonString);
            assertEquals("/hello", ((JsonString)repGlobValue).getString());
        });
        //deny privilege is not there
        assertPrivilege(groupPrivilegesObject, true, PrivilegeValues.DENY, PrivilegeConstants.JCR_READ, false, null);
    }

    @Test
    public void testModifyAceForInvalidRestrictionName() throws IOException, JsonException {
        testUserId = createTestUser();

        testFolderUrl = createTestFolder();

        // update the ACE
        List<NameValuePair> postParams = new AcePostParamsBuilder(testUserId)
                .with(":http-equiv-accept", JSONResponse.RESPONSE_CONTENT_TYPE)
                .withRestriction("invalid_name", "hello")
                .build();
        String json = addOrUpdateAce(testFolderUrl, postParams, CONTENT_TYPE_JSON, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        assertNotNull(json);

        JsonObject jsonObject = parseJson(json);
        assertEquals("javax.jcr.security.AccessControlException: Invalid restriction name was supplied", jsonObject.getString("status.message"));
    }

    @Test
    public void testModifyAceForInvalidPrivilegeName() throws IOException, JsonException {
        testUserId = createTestUser();

        testFolderUrl = createTestFolder();

        // update the ACE
        List<NameValuePair> postParams = new AcePostParamsBuilder(testUserId)
                .with(":http-equiv-accept", JSONResponse.RESPONSE_CONTENT_TYPE)
                .withPrivilege("invalid_name", PrivilegeValues.ALLOW)
                .build();
        String json = addOrUpdateAce(testFolderUrl, postParams, CONTENT_TYPE_JSON, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        assertNotNull(json);

        JsonObject jsonObject = parseJson(json);
        assertEquals("javax.jcr.security.AccessControlException: No such privilege invalid_name", jsonObject.getString("status.message"));
    }

    @Test
    public void testModifyAceForInvalidDeleteRestrictionName() throws IOException, JsonException {
        testUserId = createTestUser();

        testFolderUrl = createTestFolder();

        // update the ACE
        List<NameValuePair> postParams = new AcePostParamsBuilder(testUserId)
                .with(":http-equiv-accept", JSONResponse.RESPONSE_CONTENT_TYPE)
                .withDeletePrivilegeRestriction(PrivilegeConstants.JCR_READ, "invalid_name", DeleteValues.ALLOW)
                .build();
        String json = addOrUpdateAce(testFolderUrl, postParams, CONTENT_TYPE_JSON, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        assertNotNull(json);

        JsonObject jsonObject = parseJson(json);
        assertEquals("javax.jcr.security.AccessControlException: Invalid restriction name was supplied", jsonObject.getString("status.message"));
    }

    @Test
    public void testModifyAceForInvalidDeleteRestrictionPrivilegeName() throws IOException, JsonException {
        testUserId = createTestUser();

        testFolderUrl = createTestFolder();

        // update the ACE
        List<NameValuePair> postParams = new AcePostParamsBuilder(testUserId)
                .with(":http-equiv-accept", JSONResponse.RESPONSE_CONTENT_TYPE)
                .withDeletePrivilegeRestriction("invalid_name", AccessControlConstants.REP_GLOB, DeleteValues.ALLOW)
                .build();
        String json = addOrUpdateAce(testFolderUrl, postParams, CONTENT_TYPE_JSON, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        assertNotNull(json);

        JsonObject jsonObject = parseJson(json);
        assertEquals("javax.jcr.security.AccessControlException: No such privilege invalid_name", jsonObject.getString("status.message"));
    }

    /**
     * Test to verify adding an ACE with a custom restriction to
     * the ACL
     */
    @Test
    public void testAddAceWithCustomRestriction() throws IOException, JsonException {
        testUserId = createTestUser();
        testFolderUrl = createTestFolder();

        // update the ACE
        List<NameValuePair> postParams = new AcePostParamsBuilder(testUserId)
                .withOrder("first")
                .withPrivilegeRestriction(PrivilegeValues.ALLOW, PrivilegeConstants.JCR_READ,
                        CustomRestrictionProviderImpl.SLING_CUSTOM_RESTRICTION, new String[] {"item1", "item2"})
                .withPrivilegeRestriction(PrivilegeValues.DENY, PrivilegeConstants.JCR_WRITE,
                        CustomRestrictionProviderImpl.SLING_CUSTOM_RESTRICTION, new String[] {"item1", "item2"})
                .build();
        addOrUpdateAce(testFolderUrl, postParams);

        JsonObject user = getAce(testFolderUrl, testUserId);
        assertNotNull(user);
        assertEquals(testUserId, user.getString("principal"));
        assertEquals(0, user.getInt("order"));

        JsonObject privilegesObject = user.getJsonObject("privileges");
        assertNotNull(privilegesObject);
        assertEquals(2, privilegesObject.size());

        VerifyAce verifyRestrictions = jsonValue -> {
            assertNotNull(jsonValue);
            assertTrue(jsonValue instanceof JsonObject);
            JsonObject restrictionsObj = (JsonObject)jsonValue;

            JsonValue customValue = restrictionsObj.get(CustomRestrictionProviderImpl.SLING_CUSTOM_RESTRICTION);
            assertNotNull(customValue);
            assertTrue(customValue instanceof JsonArray);
            assertEquals(2, ((JsonArray)customValue).size());
            assertEquals("item1", ((JsonArray)customValue).getString(0));
            assertEquals("item2", ((JsonArray)customValue).getString(1));
        };
        //allow privilege
        assertPrivilege(privilegesObject, true, PrivilegeValues.ALLOW, PrivilegeConstants.JCR_READ, verifyRestrictions);
        //deny privilege
        assertPrivilege(privilegesObject, true, PrivilegeValues.DENY, PrivilegeConstants.JCR_WRITE, verifyRestrictions);
    }

    /**
     * Test to verify adding an ACE does not include an allow aggregate privilege
     * when there is a deny child privilege with the same restrictions as the parent
     */
    @Test
    public void testNoAggregateAllowWhenDenyChildHasSameRestriction() throws IOException, JsonException {
        testUserId = createTestUser();
        testFolderUrl = createTestFolder();

        // update the ACE
        List<NameValuePair> postParams = new AcePostParamsBuilder(testUserId)
                //allow the child privileges with varying restrictions
                .withPrivilege(PrivilegeConstants.REP_READ_NODES, PrivilegeValues.ALLOW)
                .withPrivilege(PrivilegeConstants.REP_READ_PROPERTIES, PrivilegeValues.ALLOW)
                .withPrivilegeRestriction(PrivilegeValues.ALLOW, PrivilegeConstants.REP_READ_PROPERTIES,
                        AccessControlConstants.REP_ITEM_NAMES, new String[] {JcrConstants.JCR_CREATED, JcrConstants.JCR_PRIMARYTYPE})
                //deny a child privilege with different restrictions than the same allow privilege
                .withPrivilege(PrivilegeConstants.REP_READ_PROPERTIES, PrivilegeValues.DENY)
                .build();
        addOrUpdateAce(testFolderUrl, postParams);

        JsonObject user = getAce(testFolderUrl, testUserId);
        assertNotNull(user);
        assertEquals(testUserId, user.getString("principal"));

        JsonObject privilegesObject = user.getJsonObject("privileges");
        assertNotNull(privilegesObject);
        assertEquals(2, privilegesObject.size());

        VerifyAce verifyRestrictions = jsonValue -> {
            assertNotNull(jsonValue);
            assertTrue(jsonValue instanceof JsonObject);
            JsonObject restrictionsObj = (JsonObject)jsonValue;

            JsonValue customValue = restrictionsObj.get(AccessControlConstants.REP_ITEM_NAMES);
            assertNotNull(customValue);
            assertTrue(customValue instanceof JsonArray);
            assertEquals(2, ((JsonArray)customValue).size());
            assertEquals(JcrConstants.JCR_CREATED, ((JsonArray)customValue).getString(0));
            assertEquals(JcrConstants.JCR_PRIMARYTYPE, ((JsonArray)customValue).getString(1));
        };
        //allow privilege
        assertPrivilege(privilegesObject, true, PrivilegeValues.ALLOW, PrivilegeConstants.REP_READ_NODES, verifyTrue);
        assertPrivilege(privilegesObject, true, PrivilegeValues.ALLOW, PrivilegeConstants.REP_READ_PROPERTIES, verifyRestrictions);
        //deny privilege
        assertPrivilege(privilegesObject, true, PrivilegeValues.DENY, PrivilegeConstants.REP_READ_PROPERTIES, verifyTrue);
    }

    /**
     * Test to verify adding an ACE does not include a deny aggregate privilege
     * when there is an allow child privilege with the same restrictions as the parent
     */
    @Test
    public void testNoAggregateDenyWhenAllowChildHasSameRestriction() throws IOException, JsonException {
        testUserId = createTestUser();
        testFolderUrl = createTestFolder();

        // update the ACE
        List<NameValuePair> postParams = new AcePostParamsBuilder(testUserId)
                //deny the child privileges with varying restrictions
                .withPrivilege(PrivilegeConstants.REP_READ_NODES, PrivilegeValues.DENY)
                .withPrivilege(PrivilegeConstants.REP_READ_PROPERTIES, PrivilegeValues.DENY)
                .withPrivilegeRestriction(PrivilegeValues.DENY, PrivilegeConstants.REP_READ_PROPERTIES,
                        AccessControlConstants.REP_ITEM_NAMES, new String[] {JcrConstants.JCR_CREATED, JcrConstants.JCR_PRIMARYTYPE})
                //allow a child privilege with different restrictions than the same deny privilege
                .withPrivilege(PrivilegeConstants.REP_READ_PROPERTIES, PrivilegeValues.ALLOW)
                .build();
        addOrUpdateAce(testFolderUrl, postParams);

        JsonObject user = getAce(testFolderUrl, testUserId);
        assertNotNull(user);
        assertEquals(testUserId, user.getString("principal"));

        JsonObject privilegesObject = user.getJsonObject("privileges");
        assertNotNull(privilegesObject);
        assertEquals(2, privilegesObject.size());

        VerifyAce verifyRestrictions = jsonValue -> {
            assertNotNull(jsonValue);
            assertTrue(jsonValue instanceof JsonObject);
            JsonObject restrictionsObj = (JsonObject)jsonValue;

            JsonValue customValue = restrictionsObj.get(AccessControlConstants.REP_ITEM_NAMES);
            assertNotNull(customValue);
            assertTrue(customValue instanceof JsonArray);
            assertEquals(2, ((JsonArray)customValue).size());
            assertEquals(JcrConstants.JCR_CREATED, ((JsonArray)customValue).getString(0));
            assertEquals(JcrConstants.JCR_PRIMARYTYPE, ((JsonArray)customValue).getString(1));
        };
        //allow privilege
        assertPrivilege(privilegesObject, true, PrivilegeValues.ALLOW, PrivilegeConstants.REP_READ_PROPERTIES, verifyTrue);
        //deny privilege
        assertPrivilege(privilegesObject, true, PrivilegeValues.DENY, PrivilegeConstants.REP_READ_NODES, verifyTrue);
        assertPrivilege(privilegesObject, true, PrivilegeValues.DENY, PrivilegeConstants.REP_READ_PROPERTIES, verifyRestrictions);
    }

}
