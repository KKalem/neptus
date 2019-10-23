/*
 * Copyright (c) 2004-2019 Universidade do Porto - Faculdade de Engenharia
 * Laboratório de Sistemas e Tecnologia Subaquática (LSTS)
 * All rights reserved.
 * Rua Dr. Roberto Frias s/n, sala I203, 4200-465 Porto, Portugal
 *
 * This file is part of Neptus, Command and Control Framework.
 *
 * Commercial Licence Usage
 * Licencees holding valid commercial Neptus licences may use this file
 * in accordance with the commercial licence agreement provided with the
 * Software or, alternatively, in accordance with the terms contained in a
 * written agreement between you and Universidade do Porto. For licensing
 * terms, conditions, and further information contact lsts@fe.up.pt.
 *
 * Modified European Union Public Licence - EUPL v.1.1 Usage
 * Alternatively, this file may be used under the terms of the Modified EUPL,
 * Version 1.1 only (the "Licence"), appearing in the file LICENCE.md
 * included in the packaging of this file. You may not use this work
 * except in compliance with the Licence. Unless required by applicable
 * law or agreed to in writing, software distributed under the Licence is
 * distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific
 * language governing permissions and limitations at
 * https://github.com/LSTS/neptus/blob/develop/LICENSE.md
 * and http://ec.europa.eu/idabc/eupl.html.
 *
 * For more information please see <http://lsts.fe.up.pt/neptus>.
 *
 * Author: Paulo Dias
 * 9/10/2011
 */
package pt.lsts.neptus.renderer2d.tiles;

import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import pt.lsts.neptus.plugins.MapTileProvider;

/**
 * @author ZP
 *
 */
@MapTileProvider(name = "Ship Traffic Density", isBaseMapOrLayer=false)
public class TileShipTrafficDensity extends TileHttpFetcher {
    
    private static final long serialVersionUID = -5210083535661616257L;

    protected static String tileClassId = TileShipTrafficDensity.class.getSimpleName();

    private static Map<String, TileShipTrafficDensity> tilesMap = Collections.synchronizedMap(new HashMap<String, TileShipTrafficDensity>());

    public TileShipTrafficDensity(Integer levelOfDetail, Integer tileX, Integer tileY, BufferedImage image) throws Exception {
        super(levelOfDetail, tileX, tileY, image);
    }
    
    
    /**
     * @param id
     * @throws Exception
     */
    public TileShipTrafficDensity(String id) throws Exception {
        super(id);
    }
    
    /**
     * @return
     */
    @Override
    protected String createTileRequestURL() {
        if (levelOfDetail > 11)
            return null;
        return "http://tiles.marinetraffic.com/ais/density_tiles/"+levelOfDetail+"/"
                +tileX+"/tile_"+levelOfDetail+"_"+tileX+"_"+tileY+".png";
    }
    
    /**
     * @return the tilesMap
     */
    @SuppressWarnings("unchecked")
    public static <T extends Tile> Map<String, T> getTilesMap() {
        return (Map<String, T>) tilesMap;
    }

    /**
     * 
     */
    public static void clearDiskCache() {
        Tile.clearDiskCache(tileClassId);
    }

    public static int getMaxLevelOfDetail() {
        return 10;
    }
    
    /**
     * @return 
     * 
     */
    public static <T extends Tile> Vector<T> loadCache() {
        return Tile.loadCache(tileClassId);
    }
}
