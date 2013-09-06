//----------------------------------------------------------------------------//
//                                                                            //
//                              P i x D i s t a n c e                         //
//                                                                            //
//----------------------------------------------------------------------------//
// <editor-fold defaultstate="collapsed" desc="hdr">                          //
//  Copyright © Herve Bitteur and others 2000-2013. All rights reserved.      //
//  This software is released under the GNU General Public License.           //
//  Goto http://kenai.com/projects/audiveris to report bugs or suggestions.   //
//----------------------------------------------------------------------------//
// </editor-fold>
package omr.image;

/**
 * Class {@code PixDistance} records a distance at a given location.
 *
 * @author Hervé Bitteur
 */
public class PixDistance
        implements Comparable<PixDistance>
{
    //~ Instance fields --------------------------------------------------------

    /** Location abscissa. */
    public final int x;

    /** Location ordinate. */
    public final int y;

    /** Distance. */
    public final double d;

    //~ Constructors -----------------------------------------------------------
    //-------------//
    // PixDistance //
    //-------------//
    /**
     * Creates a new PixDistance object.
     *
     * @param x location abscissa
     * @param y location ordinate
     * @param d measured distance at this location
     */
    public PixDistance (int x,
                          int y,
                          double d)
    {
        this.x = x;
        this.y = y;
        this.d = d;
    }

    //~ Methods ----------------------------------------------------------------
    //-----------//
    // compareTo //
    //-----------//
    @Override
    public int compareTo (PixDistance that)
    {
        return Double.compare(this.d, that.d);
    }

    //----------//
    // toString //
    //----------//
    @Override
    public String toString ()
    {
        return String.format("(x:%4d y:%4d dist:%f)", x, y, d);
    }
}
