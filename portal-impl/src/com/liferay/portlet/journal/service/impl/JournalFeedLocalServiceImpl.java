/**
 * Copyright (c) 2000-2011 Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.portlet.journal.service.impl;

import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.util.CharPool;
import com.liferay.portal.kernel.util.OrderByComparator;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.xml.Document;
import com.liferay.portal.kernel.xml.Element;
import com.liferay.portal.kernel.xml.SAXReaderUtil;
import com.liferay.portal.kernel.xml.XPath;
import com.liferay.portal.model.ResourceConstants;
import com.liferay.portal.model.User;
import com.liferay.portal.service.ServiceContext;
import com.liferay.portal.util.PortalUtil;
import com.liferay.portlet.expando.model.ExpandoBridge;
import com.liferay.portlet.journal.DuplicateFeedIdException;
import com.liferay.portlet.journal.FeedContentFieldException;
import com.liferay.portlet.journal.FeedIdException;
import com.liferay.portlet.journal.FeedNameException;
import com.liferay.portlet.journal.FeedTargetLayoutFriendlyUrlException;
import com.liferay.portlet.journal.model.JournalFeed;
import com.liferay.portlet.journal.model.JournalFeedConstants;
import com.liferay.portlet.journal.model.JournalStructure;
import com.liferay.portlet.journal.service.base.JournalFeedLocalServiceBaseImpl;
import com.liferay.util.RSSUtil;

import java.util.Date;
import java.util.List;

/**
 * @author Raymond Augé
 */
public class JournalFeedLocalServiceImpl
	extends JournalFeedLocalServiceBaseImpl {

	public JournalFeed addFeed(
			long userId, long groupId, String feedId, boolean autoFeedId,
			String name, String description, String type, String structureId,
			String templateId, String rendererTemplateId, int delta,
			String orderByCol, String orderByType,
			String targetLayoutFriendlyUrl, String targetPortletId,
			String contentField, String feedType, double feedVersion,
			ServiceContext serviceContext)
		throws PortalException, SystemException {

		// Feed

		User user = userPersistence.findByPrimaryKey(userId);
		feedId = feedId.trim().toUpperCase();
		Date now = new Date();

		validate(
			user.getCompanyId(), groupId, feedId, autoFeedId, name, structureId,
			targetLayoutFriendlyUrl, contentField);

		if (autoFeedId) {
			feedId = String.valueOf(counterLocalService.increment());
		}

		long id = counterLocalService.increment();

		JournalFeed feed = journalFeedPersistence.create(id);

		feed.setUuid(serviceContext.getUuid());
		feed.setGroupId(groupId);
		feed.setCompanyId(user.getCompanyId());
		feed.setUserId(user.getUserId());
		feed.setUserName(user.getFullName());
		feed.setCreateDate(serviceContext.getCreateDate(now));
		feed.setModifiedDate(serviceContext.getModifiedDate(now));
		feed.setFeedId(feedId);
		feed.setName(name);
		feed.setDescription(description);
		feed.setType(type);
		feed.setStructureId(structureId);
		feed.setTemplateId(templateId);
		feed.setRendererTemplateId(rendererTemplateId);
		feed.setDelta(delta);
		feed.setOrderByCol(orderByCol);
		feed.setOrderByType(orderByType);
		feed.setTargetLayoutFriendlyUrl(targetLayoutFriendlyUrl);
		feed.setTargetPortletId(targetPortletId);
		feed.setContentField(contentField);

		if (Validator.isNull(feedType)) {
			feed.setFeedType(RSSUtil.DEFAULT_TYPE);
			feed.setFeedVersion(RSSUtil.DEFAULT_VERSION);
		}
		else {
			feed.setFeedType(feedType);
			feed.setFeedVersion(feedVersion);
		}

		journalFeedPersistence.update(feed, false);

		// Resources

		if (serviceContext.getAddCommunityPermissions() ||
			serviceContext.getAddGuestPermissions()) {

			addFeedResources(
				feed, serviceContext.getAddCommunityPermissions(),
				serviceContext.getAddGuestPermissions());
		}
		else {
			addFeedResources(
				feed, serviceContext.getCommunityPermissions(),
				serviceContext.getGuestPermissions());
		}

		// Expando

		ExpandoBridge expandoBridge = feed.getExpandoBridge();

		expandoBridge.setAttributes(serviceContext);

		return feed;
	}

	public void addFeedResources(
			long feedId, boolean addCommunityPermissions,
			boolean addGuestPermissions)
		throws PortalException, SystemException {

		JournalFeed feed = journalFeedPersistence.findByPrimaryKey(feedId);

		addFeedResources(feed, addCommunityPermissions, addGuestPermissions);
	}

	public void addFeedResources(
			JournalFeed feed, boolean addCommunityPermissions,
			boolean addGuestPermissions)
		throws PortalException, SystemException {

		resourceLocalService.addResources(
			feed.getCompanyId(), feed.getGroupId(), feed.getUserId(),
			JournalFeed.class.getName(), feed.getId(), false,
			addCommunityPermissions, addGuestPermissions);
	}

	public void addFeedResources(
			long feedId, String[] communityPermissions,
			String[] guestPermissions)
		throws PortalException, SystemException {

		JournalFeed feed = journalFeedPersistence.findByPrimaryKey(feedId);

		addFeedResources(feed, communityPermissions, guestPermissions);
	}

	public void addFeedResources(
			JournalFeed feed, String[] communityPermissions,
			String[] guestPermissions)
		throws PortalException, SystemException {

		resourceLocalService.addModelResources(
			feed.getCompanyId(), feed.getGroupId(), feed.getUserId(),
			JournalFeed.class.getName(), feed.getId(), communityPermissions,
			guestPermissions);
	}

	public void deleteFeed(long feedId)
		throws PortalException, SystemException {

		JournalFeed feed = journalFeedPersistence.findByPrimaryKey(feedId);

		deleteFeed(feed);
	}

	public void deleteFeed(long groupId, String feedId)
		throws PortalException, SystemException {

		JournalFeed feed = journalFeedPersistence.findByG_F(groupId, feedId);

		deleteFeed(feed);
	}

	public void deleteFeed(JournalFeed feed)
		throws PortalException, SystemException {

		// Expando

		expandoValueLocalService.deleteValues(
			JournalFeed.class.getName(), feed.getId());

		// Resources

		resourceLocalService.deleteResource(
			feed.getCompanyId(), JournalFeed.class.getName(),
			ResourceConstants.SCOPE_INDIVIDUAL, feed.getId());

		// Feed

		journalFeedPersistence.remove(feed);
	}

	public JournalFeed getFeed(long feedId)
		throws PortalException, SystemException {

		return journalFeedPersistence.findByPrimaryKey(feedId);
	}

	public JournalFeed getFeed(long groupId, String feedId)
		throws PortalException, SystemException {

		return journalFeedPersistence.findByG_F(groupId, feedId);
	}

	public List<JournalFeed> getFeeds() throws SystemException {
		return journalFeedPersistence.findAll();
	}

	public List<JournalFeed> getFeeds(long groupId) throws SystemException {
		return journalFeedPersistence.findByGroupId(groupId);
	}

	public List<JournalFeed> getFeeds(long groupId, int start, int end)
		throws SystemException {

		return journalFeedPersistence.findByGroupId(groupId, start, end);
	}

	public int getFeedsCount(long groupId) throws SystemException {
		return journalFeedPersistence.countByGroupId(groupId);
	}

	public List<JournalFeed> search(
			long companyId, long groupId, String keywords, int start, int end,
			OrderByComparator obc)
		throws SystemException {

		return journalFeedFinder.findByKeywords(
			companyId, groupId, keywords, start, end, obc);
	}

	public List<JournalFeed> search(
			long companyId, long groupId, String feedId, String name,
			String description, boolean andOperator, int start, int end,
			OrderByComparator obc)
		throws SystemException {

		return journalFeedFinder.findByC_G_F_N_D(
			companyId, groupId, feedId, name, description, andOperator, start,
			end, obc);
	}

	public int searchCount(long companyId, long groupId, String keywords)
		throws SystemException {

		return journalFeedFinder.countByKeywords(
			companyId, groupId, keywords);
	}

	public int searchCount(
			long companyId, long groupId, String feedId, String name,
			String description, boolean andOperator)
		throws SystemException {

		return journalFeedFinder.countByC_G_F_N_D(
			companyId, groupId, feedId, name, description, andOperator);
	}

	public JournalFeed updateFeed(
			long groupId, String feedId, String name, String description,
			String type, String structureId, String templateId,
			String rendererTemplateId, int delta, String orderByCol,
			String orderByType, String targetLayoutFriendlyUrl,
			String targetPortletId, String contentField, String feedType,
			double feedVersion, ServiceContext serviceContext)
		throws PortalException, SystemException{

		// Feed

		JournalFeed feed = journalFeedPersistence.findByG_F(groupId, feedId);

		validate(
			feed.getCompanyId(), groupId, name, structureId,
			targetLayoutFriendlyUrl, contentField);

		feed.setModifiedDate(serviceContext.getModifiedDate(null));
		feed.setName(name);
		feed.setDescription(description);
		feed.setType(type);
		feed.setStructureId(structureId);
		feed.setTemplateId(templateId);
		feed.setRendererTemplateId(rendererTemplateId);
		feed.setDelta(delta);
		feed.setOrderByCol(orderByCol);
		feed.setOrderByType(orderByType);
		feed.setTargetLayoutFriendlyUrl(targetLayoutFriendlyUrl);
		feed.setTargetPortletId(targetPortletId);
		feed.setContentField(contentField);

		if (Validator.isNull(feedType)) {
			feed.setFeedType(RSSUtil.DEFAULT_TYPE);
			feed.setFeedVersion(RSSUtil.DEFAULT_VERSION);
		}
		else {
			feed.setFeedType(feedType);
			feed.setFeedVersion(feedVersion);
		}

		journalFeedPersistence.update(feed, false);

		// Expando

		ExpandoBridge expandoBridge = feed.getExpandoBridge();

		expandoBridge.setAttributes(serviceContext);

		return feed;
	}

	protected boolean isValidStructureField(
		long groupId, String structureId, String contentField) {

		if (contentField.equals(JournalFeedConstants.WEB_CONTENT_DESCRIPTION) ||
			contentField.equals(JournalFeedConstants.RENDERED_WEB_CONTENT)) {

			return true;
		}
		else {
			try {
				JournalStructure structure =
					journalStructurePersistence.findByG_S(groupId, structureId);

				Document doc = SAXReaderUtil.read(structure.getXsd());

				XPath xpathSelector = SAXReaderUtil.createXPath(
					"//dynamic-element[@name='"+ contentField + "']");

				Element el = (Element)xpathSelector.selectSingleNode(doc);

				if (el != null) {
					return true;
				}
			}
			catch (Exception e) {
			}
		}

		return false;
	}

	protected void validate(
			long companyId, long groupId, String feedId, boolean autoFeedId,
			String name, String structureId, String targetLayoutFriendlyUrl,
			String contentField)
		throws PortalException, SystemException {

		if (!autoFeedId) {
			if ((Validator.isNull(feedId)) || (Validator.isNumber(feedId)) ||
				(feedId.indexOf(CharPool.SPACE) != -1)) {

				throw new FeedIdException();
			}

			JournalFeed feed = journalFeedPersistence.fetchByG_F(
				groupId, feedId);

			if (feed != null) {
				throw new DuplicateFeedIdException();
			}
		}

		validate(
			companyId, groupId, name, structureId, targetLayoutFriendlyUrl,
			contentField);
	}

	protected void validate(
			long companyId, long groupId, String name, String structureId,
			String targetLayoutFriendlyUrl, String contentField)
		throws PortalException {

		if (Validator.isNull(name)) {
			throw new FeedNameException();
		}

		long plid = PortalUtil.getPlidFromFriendlyURL(
			companyId, targetLayoutFriendlyUrl);

		if (plid <= 0) {
			throw new FeedTargetLayoutFriendlyUrlException();
		}

		if (!isValidStructureField(groupId, structureId, contentField)) {
			throw new FeedContentFieldException();
		}
	}

}