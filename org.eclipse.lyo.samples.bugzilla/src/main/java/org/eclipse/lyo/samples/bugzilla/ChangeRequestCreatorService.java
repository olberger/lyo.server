/*******************************************************************************
 * Copyright (c) 2011, 2012 IBM Corporation.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 *  
 *  The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 *  and the Eclipse Distribution License is available at
 *  http://www.eclipse.org/org/documents/edl-v10.php.
 *  
 *  Contributors:
 *  
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.lyo.samples.bugzilla;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.lyo.samples.bugzilla.exception.UnauthroziedException;
import org.eclipse.lyo.samples.bugzilla.jbugzx.rpc.GetLegalValues;
import org.eclipse.lyo.samples.bugzilla.utils.HttpUtils;
import org.eclipse.lyo.samples.bugzilla.utils.StringUtils;

import com.j2bugzilla.base.Bug;
import com.j2bugzilla.base.BugFactory;
import com.j2bugzilla.base.BugzillaConnector;
import com.j2bugzilla.base.Product;
import com.j2bugzilla.rpc.GetProduct;
import com.j2bugzilla.rpc.ReportBug;


/**
 * GET returns Delegated UI and POST accepts form post of new bug data.
 * Relies on new J2Bugzilla methods: GetProducts, GetAccessibleProducts and GetLegalValues.
 */
public class ChangeRequestCreatorService extends HttpServlet {
	private static final long serialVersionUID = 7466374797163202313L;
	
	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		Product product = null;
		try {
			BugzillaConnector bc = BugzillaInitializer.getBugzillaConnector(request);
			
			int productId = Integer.parseInt(request.getParameter("productId"));
			
			GetProduct getProducts = new GetProduct(productId);
			bc.executeMethod(getProducts);
			product = getProducts.getProduct();
			request.setAttribute("product", product);
			
		} catch (UnauthroziedException e) {
			HttpUtils.sendUnauthorizedResponse(response, e);
			return;
		} catch (Exception e) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}		
		
		try {				
			BugzillaConnector bc = BugzillaInitializer.getBugzillaConnector(request);

			GetLegalValues getComponentValues = 
				new GetLegalValues("component", product.getID());
			bc.executeMethod(getComponentValues);
			List<String> components = Arrays.asList(getComponentValues.getValues());
			request.setAttribute("components", components);
			
			GetLegalValues getOsValues = new GetLegalValues("op_sys", -1);
			bc.executeMethod(getOsValues);
			List<String> operatingSystems = Arrays.asList(getOsValues.getValues());
			request.setAttribute("operatingSystems", operatingSystems);
			
			GetLegalValues getPlatformValues = new GetLegalValues("platform", -1);
			bc.executeMethod(getPlatformValues);
			List<String> platforms = Arrays.asList(getPlatformValues.getValues());
			request.setAttribute("platforms", platforms);
			
			GetLegalValues getVersionValues = new GetLegalValues("version", product.getID());
			bc.executeMethod(getVersionValues);
			List<String> versions = Arrays.asList(getVersionValues.getValues());
			request.setAttribute("versions", versions);
			
	        request.setAttribute("bugzillaUri", BugzillaInitializer.getBugzillaUri());

	        RequestDispatcher rd = request.getRequestDispatcher("/cm/changerequest_creator.jsp");
    		rd.forward(request, response);
			
		} catch (UnauthroziedException e) {
			HttpUtils.sendUnauthorizedResponse(response, e);
		} catch (Exception e) {
			throw new ServletException(e);
		}
	}


	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        System.err.println("CRCreator.doPOST - Accept: " + request.getHeader("Accept") + ", query="+request.getQueryString());

		try {	
        
			int productId = Integer.parseInt(request.getParameter("productId"));

			String prefill   = request.getParameter("prefill");

			if (StringUtils.isEmpty(prefill) || "true".equals(prefill)) {
				// TODO: Implement form prefill, just return same dialog URL in Location for now
				response.setHeader("Location", URLStrategy.getDelegatedCreationURL(productId));
				response.setStatus(201);
				return;

			}


			BugzillaConnector bc = BugzillaInitializer.getBugzillaConnector(request);				
			GetProduct getProducts = new GetProduct(productId); 
			bc.executeMethod(getProducts);
			Product product = getProducts.getProduct();

			String summary   = request.getParameter("summary"); 
			String component = request.getParameter("component");
			String version   = request.getParameter("version"); 
			String operatingSystem = request.getParameter("op_sys"); 
			String platform  = request.getParameter("platform");
			String description = request.getParameter("description");

			BugFactory factory = new BugFactory().newBug().setProduct(product.getName());
			if (summary != null) {
				factory.setSummary(summary);
			}
			if (version != null) {
				factory.setVersion(version);
			}
			if (component != null) {
				factory.setComponent(component);
			}
			if (platform != null) {
				factory.setPlatform(platform);
			}
			if (operatingSystem != null) {
				factory.setOperatingSystem(operatingSystem);
			}
			if (description != null) {
				factory.setDescription(description);
			}
			
			Bug bug = factory.createBug();
			ReportBug reportBug = new ReportBug(bug);			
			bc.executeMethod(reportBug);

			// Send back to the form a small JSON response
			response.setContentType("application/json");
			response.setStatus(201); // Created
			PrintWriter out = response.getWriter();
			out.print("{\"title\": \"" + URLStrategy.getChangeRequestLinkLabel(reportBug.getID(), summary) + "\"," +
					"\"resource\" : \"" + URLStrategy.getChangeRequestURL(reportBug.getID()) + "\"}");
			out.close();
    		
		} catch (UnauthroziedException e) {
			HttpUtils.sendUnauthorizedResponse(response, e);
		} catch (Exception e) {
			throw new ServletException(e);
		}
	}


}
