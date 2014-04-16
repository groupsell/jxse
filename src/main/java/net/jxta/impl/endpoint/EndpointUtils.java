/*
 *  Copyright (c) 2001-2004 Sun Microsystems, Inc. All rights reserved.
 *
 *  The Sun Project JXTA(TM) Software License
 *
 *  Redistribution and use in source and binary forms, with or without 
 *  modification, are permitted provided that the following conditions are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright notice, 
 *     this list of conditions and the following disclaimer in the documentation 
 *     and/or other materials provided with the distribution.
 *
 *  3. The end-user documentation included with the redistribution, if any, must 
 *     include the following acknowledgment: "This product includes software 
 *     developed by Sun Microsystems, Inc. for JXTA(TM) technology." 
 *     Alternately, this acknowledgment may appear in the software itself, if 
 *     and wherever such third-party acknowledgments normally appear.
 *
 *  4. The names "Sun", "Sun Microsystems, Inc.", "JXTA" and "Project JXTA" must 
 *     not be used to endorse or promote products derived from this software 
 *     without prior written permission. For written permission, please contact 
 *     Project JXTA at http://www.jxta.org.
 *
 *  5. Products derived from this software may not be called "JXTA", nor may 
 *     "JXTA" appear in their name, without prior written permission of Sun.
 *
 *  THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED WARRANTIES,
 *  INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND 
 *  FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL SUN 
 *  MICROSYSTEMS OR ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, 
 *  INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT 
 *  LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, 
 *  OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF 
 *  LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING 
 *  NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, 
 *  EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 *  JXTA is a registered trademark of Sun Microsystems, Inc. in the United 
 *  States and other countries.
 *
 *  Please see the license information page at :
 *  <http://www.jxta.org/project/www/license.html> for instructions on use of 
 *  the license in source files.
 *
 *  ====================================================================
 *
 *  This software consists of voluntary contributions made by many individuals 
 *  on behalf of Project JXTA. For more information on Project JXTA, please see 
 *  http://www.jxta.org.
 *
 *  This license is based on the BSD license adopted by the Apache Foundation. 
 */

package net.jxta.impl.endpoint;

import net.jxta.document.AdvertisementFactory;
import net.jxta.document.XMLElement;
import net.jxta.logging.Logging;
import net.jxta.peergroup.IModuleDefinitions;
import net.jxta.protocol.PeerAdvertisement;
import net.jxta.protocol.RouteAdvertisement;

import java.util.Enumeration;
import java.util.logging.Logger;

/**
 *  Utility functions related to the Endpoint Service.
 */
public final class EndpointUtils {

    /**
     * Logger
     */
    private static final transient Logger LOG = Logger.getLogger(EndpointUtils.class.getName());

    /**
     * Extracts a route advertisement from a peer advertisement.
     *
     * @param adv The peer advertisement believed to contain a route advertisement.
     * @return The extracted route advertisement or {@code null} if a route
     * advertisement could not be extracted.
     */
    public static RouteAdvertisement extractRouteAdv(PeerAdvertisement adv) {

        try {

            // Get its EndpointService advertisement
            XMLElement endpParam = (XMLElement) adv.getServiceParam(IModuleDefinitions.endpointClassID);

            if (endpParam == null) {
                Logging.logCheckedFine(LOG, "No Endpoint Params");
                return null;
            }

            // get the Route Advertisement element
            Enumeration paramChilds = endpParam.getChildren(RouteAdvertisement.getAdvertisementType());
            XMLElement param;

            if (paramChilds.hasMoreElements()) {
                param = (XMLElement) paramChilds.nextElement();
            } else {
                Logging.logCheckedFine(LOG, "No Route Adv in Peer Adv");
                return null;
            }

            // build the new route
            RouteAdvertisement route = (RouteAdvertisement) AdvertisementFactory.newAdvertisement(param);

            // Embedded routes often don't contain the peer id.
            route.setDestPeerID(adv.getPeerID());

            return route;

        } catch (Exception e) {

            Logging.logCheckedWarning(LOG, "failed to extract radv\n", e);

        }

        return null;
    }
}
