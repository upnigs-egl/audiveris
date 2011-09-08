//----------------------------------------------------------------------------//
//                                                                            //
//                           P a g e P a i n t e r                            //
//                                                                            //
//----------------------------------------------------------------------------//
// <editor-fold defaultstate="collapsed" desc="hdr">                          //
//  Copyright (C) Herve Bitteur 2000-2010. All rights reserved.               //
//  This software is released under the GNU General Public License.           //
//  Goto http://kenai.com/projects/audiveris to report bugs or suggestions.   //
//----------------------------------------------------------------------------//
// </editor-fold>
package omr.score.ui;

import omr.constant.Constant;
import omr.constant.ConstantSet;

import omr.glyph.Shape;
import static omr.glyph.Shape.*;
import omr.glyph.ShapeRange;
import omr.glyph.facets.Glyph;
import omr.glyph.text.Sentence;

import omr.log.Logger;

import omr.score.Score;
import omr.score.common.PixelDimension;
import omr.score.common.PixelPoint;
import omr.score.common.PixelRectangle;
import omr.score.entity.Arpeggiate;
import omr.score.entity.Articulation;
import omr.score.entity.Beam;
import omr.score.entity.Chord;
import omr.score.entity.Clef;
import omr.score.entity.Coda;
import omr.score.entity.Dynamics;
import omr.score.entity.Fermata;
import omr.score.entity.KeySignature;
import omr.score.entity.MeasureElement;
import omr.score.entity.Note;
import omr.score.entity.Ornament;
import omr.score.entity.Pedal;
import omr.score.entity.ScoreSystem;
import omr.score.entity.Segno;
import omr.score.entity.Slur;
import omr.score.entity.Staff;
import omr.score.entity.SystemPart;
import omr.score.entity.Text;
import omr.score.entity.TimeSignature;
import omr.score.entity.TimeSignature.InvalidTimeSignature;
import omr.score.entity.Tuplet;
import omr.score.entity.Wedge;
import omr.score.visitor.AbstractScoreVisitor;

import omr.sheet.Scale;
import omr.sheet.SystemInfo;

import omr.ui.symbol.Alignment;
import static omr.ui.symbol.Alignment.*;
import static omr.ui.symbol.Alignment.Horizontal.*;
import static omr.ui.symbol.Alignment.Vertical.*;
import omr.ui.symbol.MusicFont;
import omr.ui.symbol.OmrFont;
import omr.ui.symbol.ShapeSymbol;
import omr.ui.symbol.Symbols;
import static omr.ui.symbol.Symbols.*;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.util.ConcurrentModificationException;

/**
 * Class <code>PagePainter</code> is an abstract class that defines common
 * features of a page painter.
 * <p>It is specialized by: <ul>
 * <li>{@link PagePhysicalPainter} for the presentation of page entities over
 * the sheet glyphs</li>
 * <li>We used to also have a PageLogicalPainter for the "ideal" score view</li>
 *
 * @author Hervé Bitteur
 */
public abstract class PagePainter
    extends AbstractScoreVisitor
{
    //~ Static fields/initializers ---------------------------------------------

    /** Specific application parameters */
    private static final Constants constants = new Constants();

    /** Usual logger utility */
    private static final Logger logger = Logger.getLogger(PagePainter.class);

    /** The alignment used by default */
    protected static final Alignment defaultAlignment = AREA_CENTER;

    /** A transformation to half scale (used for slot time annotation) */
    protected static final AffineTransform halfAT = AffineTransform.getScaleInstance(
        0.5,
        0.5);

    /** Font for annotations */
    protected static final Font basicFont = new Font(
        "Sans Serif",
        Font.PLAIN,
        constants.basicFontSize.getValue());

    /** Color for music symbols */
    public static Color musicColor = constants.musicColor.getValue();

    /** Abscissa offset, in pixels, for annotation near system */
    protected static int annotationDx = 15;

    /** Ordinate offset, in pixels, for annotation near staff or system */
    protected static int annotationDy = 15;

    //~ Instance fields --------------------------------------------------------

    // Graphic context
    protected final Graphics2D g;

    // Should we draw annotations?
    protected final boolean annotated;

    // Related score
    protected Score       score;

    // Specific font for music symbols
    protected MusicFont   musicFont;

    // Global scale
    protected Scale       scale;

    // For staff lines
    protected int         lineThickness;
    protected Stroke      lineStroke;

    // For stems
    protected float       stemThickness;
    protected float       stemHalfThickness;
    protected Stroke      stemStroke;

    // For beams
    protected float       beamThickness;
    protected float       beamHalfThickness;

    // The system being currently painted
    protected ScoreSystem system;
    protected SystemInfo  systemInfo;

    //~ Constructors -----------------------------------------------------------

    //--------------//
    // PagePainter //
    //--------------//
    /**
     * Creates a new PagePainter object.
     *
     * @param graphics Graphic context
     * @param annotated true if annotations are to be drawn
     */
    public PagePainter (Graphics graphics,
                        boolean  annotated)
    {
        g = (Graphics2D) graphics.create();
        this.annotated = annotated;

        // Anti-aliasing
        g.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON);

        // Default font for annotations
        g.setFont(basicFont);
    }

    //~ Methods ----------------------------------------------------------------

    //------------------//
    // visit Arpeggiate //
    //------------------//
    @Override
    public boolean visit (Arpeggiate arpeggiate)
    {
        try {
            // Draw an arpeggiate symbol with proper height
            // Using half-height of arpeggiate character as the elementary unit
            // We need clipping to draw half characters
            final Rectangle      oldClip = g.getClipBounds();
            final PixelRectangle box = arpeggiate.getBox();
            box.height -= 2; // Gives better results

            // How many half arpeggiate symbols do we need?
            final int        halfHeight = scale.interline();
            final int        count = (int) Math.rint(
                (double) box.height / halfHeight);
            final TextLayout layout = musicFont.layout(ARPEGGIATO);
            final Point      start = new Point(box.x, box.y + box.height);

            // Draw count * half symbols, bottom up
            for (int i = 0; i < count; i++) {
                // Define a clipping area
                final Rectangle area = new Rectangle(
                    start.x,
                    start.y - halfHeight,
                    box.width,
                    halfHeight);
                area.grow(6, 6); // Add some margin to avoid gaps

                final Rectangle clip = oldClip.intersection(area);

                // Anything to draw in the clipping area?
                if ((clip.width > 0) && (clip.height > 0)) {
                    g.setClip(clip);
                    layout.draw(g, start.x, start.y);
                }

                // Move up half height
                start.y -= halfHeight;
            }

            // Restore clip
            g.setClip(oldClip);
        } catch (ConcurrentModificationException ignored) {
        } catch (Exception ex) {
            logger.warning(
                getClass().getSimpleName() + " Error visiting " + arpeggiate,
                ex);
        }

        return true;
    }

    //--------------------//
    // visit Articulation //
    //--------------------//
    @Override
    public boolean visit (Articulation articulation)
    {
        return visit((MeasureElement) articulation);
    }

    //------------//
    // visit Beam //
    //------------//
    @Override
    public boolean visit (Beam beam)
    {
        try {
            final PixelPoint left = new PixelPoint(beam.getLeftPoint());
            final PixelPoint right = new PixelPoint(beam.getRightPoint());
            final int        dx = (int) Math.rint(stemHalfThickness);
            final int        dy = (int) Math.rint(beamHalfThickness);

            // Compute precise abscissae values
            if (beam.isHook()) {
                // Just a hook stuck to a stem on one side
                if (!beam.getChords()
                         .isEmpty()) {
                    Chord chord = beam.getChords()
                                      .first();

                    if (chord.getCenter().x < beam.getCenter().x) {
                        left.x -= dx;
                    } else {
                        right.x += dx;
                    }
                } else {
                    //                beam.addError(
                    //                    beam.getGlyphs().iterator().next(),
                    //                    "Beam hook with no related chord");
                    return false;
                }
            } else {
                // Standard beam stuck to 2 stems, one on either side
                left.x -= dx;
                right.x += dx;
            }

            // Use a filled polygon to paint the beam
            final Polygon polygon = new Polygon();
            polygon.addPoint(left.x, left.y - dy);
            polygon.addPoint(left.x, left.y + dy);
            polygon.addPoint(right.x, right.y + dy);
            polygon.addPoint(right.x, right.y - dy);
            g.fill(polygon);
        } catch (ConcurrentModificationException ignored) {
        } catch (Exception ex) {
            logger.warning(
                getClass().getSimpleName() + " Error visiting " + beam,
                ex);
        }

        return true;
    }

    //-------------//
    // visit Chord //
    //-------------//
    @Override
    public boolean visit (Chord chord)
    {
        try {
            if (chord.getStem() == null) {
                return true;
            }

            final PixelPoint tail = chord.getTailLocation();
            final PixelPoint head = chord.getHeadLocation();

            if ((tail == null) || (head == null)) {
                chord.addError("No head - tail defined for chord");

                return false;
            }

            // Flags ?
            final int fn = chord.getFlagsNumber();

            if (fn > 0) {
                // We draw from tail
                boolean    goesUp = head.y < tail.y;
                PixelPoint loc = location(tail, chord);
                paint(
                    Chord.getFlagShape(fn, goesUp),
                    location(tail, chord),
                    goesUp ? BOTTOM_LEFT : TOP_LEFT);
            }
        } catch (ConcurrentModificationException ignored) {
        } catch (Exception ex) {
            logger.warning(
                getClass().getSimpleName() + " Error visiting " + chord,
                ex);
        }

        return true;
    }

    //------------//
    // visit Clef //
    //------------//
    @Override
    public boolean visit (Clef clef)
    {
        try {
            paint(clef.getShape(), clef.getReferencePoint());
        } catch (ConcurrentModificationException ignored) {
        } catch (Exception ex) {
            logger.warning(
                getClass().getSimpleName() + " Error visiting " + clef,
                ex);
        }

        return true;
    }

    //------------//
    // visit Coda //
    //------------//
    @Override
    public boolean visit (Coda coda)
    {
        return visit((MeasureElement) coda);
    }

    //----------------//
    // visit Dynamics //
    //----------------//
    @Override
    public boolean visit (Dynamics dynamics)
    {
        return visit((MeasureElement) dynamics);
    }

    //---------------//
    // visit Fermata //
    //---------------//
    @Override
    public boolean visit (Fermata fermata)
    {
        return visit((MeasureElement) fermata);
    }

    //--------------------//
    // visit KeySignature //
    //--------------------//
    @Override
    public boolean visit (KeySignature keySignature)
    {
        try {
            final Staff          staff = keySignature.getStaff();
            final Shape          clefKind = keySignature.getClefKind();
            final int            key = keySignature.getKey();
            final int            sign = Integer.signum(key);
            final Shape          shape = (key < 0) ? FLAT : SHARP;
            final TextLayout     layout = musicFont.layout(shape);
            final PixelRectangle box = keySignature.getBox();
            final int            unitDx = getKeySigItemDx();

            if (box == null) {
                ///logger.warning("Null box for " + keySignature);
                keySignature.addError("Null box for " + keySignature);

                return false;
            }

            // Flats : use vertical stick on left
            // Sharps : use center of the two vertical sticks
            final Alignment alignment = new Alignment(
                BASELINE,
                (key < 0) ? LEFT : CENTER);
            PixelPoint      point = new PixelPoint(box.x, 0);

            for (int i = 1; i <= (key * sign); i++) {
                int     n = i * sign;
                double  pitch = KeySignature.getItemPosition(n, clefKind);
                Integer ref = keySignature.getItemPixelAbscissa(n);

                if (ref != null) {
                    ///logger.info(n + ":" + ref + " for " + keySignature);
                    point = new PixelPoint(ref, 0);
                }

                paint(layout, location(point, staff, pitch), alignment);
                point.x += unitDx; // Fall-back if ref is not known
            }
        } catch (ConcurrentModificationException ignored) {
        } catch (Exception ex) {
            logger.warning(
                getClass().getSimpleName() + " Error visiting " + keySignature,
                ex);
        }

        return true;
    }

    //----------------------//
    // visit MeasureElement //
    //----------------------//
    @Override
    public boolean visit (MeasureElement measureElement)
    {
        try {
            if (measureElement.getShape() != null) {
                try {
                    paint(
                        musicFont.layout(
                            measureElement.getShape(),
                            measureElement.getDimension()),
                        measureElement.getReferencePoint());
                } catch (ConcurrentModificationException ignored) {
                } catch (Exception ex) {
                    logger.warning("Cannot paint " + measureElement, ex);
                }
            }
        } catch (ConcurrentModificationException ignored) {
        } catch (Exception ex) {
            logger.warning(
                getClass().getSimpleName() + " Error visiting " +
                measureElement,
                ex);
        }

        return true;
    }

    //------------//
    // visit Note //
    //------------//
    @Override
    public boolean visit (Note note)
    {
        try {
            final Chord      chord = note.getChord();
            final Glyph      stem = chord.getStem();
            final Shape      shape = note.getShape();
            final PixelPoint center = note.getCenter();

            // Note head
            if (stem != null) {
                // Note is attached to a stem, link note display to the stem
                paint(
                    ShapeRange.HeadAndFlags.contains(shape) ? NOTEHEAD_BLACK
                                        : shape,
                    noteLocation(note),
                    (center.x < chord.getTailLocation().x) ? MIDDLE_RIGHT
                                        : MIDDLE_LEFT);
            } else {
                // Standard display
                paint(shape.getPhysicalShape(), noteLocation(note));
            }

            // Accidental ?
            final Glyph accid = note.getAccidental();

            if (accid != null) {
                paint(
                    accid.getShape(),
                    accidentalLocation(note, accid),
                    BASELINE_CENTER);
            }
        } catch (ConcurrentModificationException ignored) {
        } catch (Exception ex) {
            logger.warning(
                getClass().getSimpleName() + " Error visiting " + note,
                ex);
        }

        return true;
    }

    //----------------//
    // visit Ornament //
    //----------------//
    @Override
    public boolean visit (Ornament ornament)
    {
        return visit((MeasureElement) ornament);
    }

    //-------------//
    // visit Pedal //
    //-------------//
    @Override
    public boolean visit (Pedal pedal)
    {
        return visit((MeasureElement) pedal);
    }

    //-------------//
    // visit Segno //
    //-------------//
    @Override
    public boolean visit (Segno segno)
    {
        return visit((MeasureElement) segno);
    }

    //------------//
    // visit Slur //
    //------------//
    @Override
    public boolean visit (Slur slur)
    {
        try {
            g.draw(slur.getCurve());
        } catch (ConcurrentModificationException ignored) {
        } catch (Exception ex) {
            logger.warning(
                getClass().getSimpleName() + " Error visiting " + slur,
                ex);
        }

        return true;
    }

    //------------------//
    // visit SystemPart //
    //------------------//
    @Override
    public boolean visit (SystemPart part)
    {
        try {
            // We don't draw dummy parts?
            if (part.isDummy()) {
                return false;
            }

            // Draw a brace?
            if (part.getBrace() != null) {
                // We have nice half braces in MusicalSymbols font
                final PixelRectangle box = braceBox(part);
                final PixelPoint     center = box.getCenter();
                final PixelDimension halfDim = new PixelDimension(
                    box.width,
                    box.height / 2);
                paint(
                    musicFont.layout(SYMBOL_BRACE_UPPER_HALF, halfDim),
                    center,
                    BOTTOM_CENTER);
                paint(
                    musicFont.layout(SYMBOL_BRACE_LOWER_HALF, halfDim),
                    center,
                    TOP_CENTER);
            }

            // Render the part starting barline, if any
            if (part.getStartingBarline() != null) {
                part.getStartingBarline()
                    .accept(this);
            }
        } catch (ConcurrentModificationException ignored) {
        } catch (Exception ex) {
            logger.warning(
                getClass().getSimpleName() + " Error visiting " + part,
                ex);
        }

        return true;
    }

    //--------------//
    // visit Tuplet //
    //--------------//
    @Override
    public boolean visit (Tuplet tuplet)
    {
        return visit((MeasureElement) tuplet);
    }

    //------------//
    // visit Text //
    //------------//
    @Override
    public boolean visit (Text text)
    {
        try {
            // Force y alignment for items of the same sentence
            final Sentence   sentence = text.getSentence();
            final PixelPoint location = sentence.getLocation();
            final PixelPoint refPoint = text.getReferencePoint();
            final String     str = text.getContent();
            final Font       font = text.getFont();

            if ((str != null) && (font != null)) {
                final FontRenderContext frc = g.getFontRenderContext();
                TextLayout              layout = new TextLayout(str, font, frc);

                paint(
                    layout,
                    new PixelPoint(refPoint.x, location.y),
                    BASELINE_LEFT);
            }
        } catch (ConcurrentModificationException ignored) {
        } catch (Exception ex) {
            logger.warning(
                getClass().getSimpleName() + " Error visiting " + text,
                ex);
        }

        return true;
    }

    //---------------------//
    // visit TimeSignature //
    //---------------------//
    @Override
    public boolean visit (TimeSignature timeSignature)
    {
        try {
            final Shape      shape = timeSignature.getShape();
            final PixelPoint center = timeSignature.getCenter();
            final Staff      staff = timeSignature.getStaff();
            final int        dy = staff.pitchToPixels(-2);

            if (shape == Shape.NO_LEGAL_TIME) {
                // If this is an illegal shape, do not draw anything.
                // TODO: we could draw a special sign for this
                return false;
            }

            // Special symbol?
            if ((shape == COMMON_TIME) || (shape == CUT_TIME)) {
                paint(shape, center);
            } else {
                // Paint numerator
                paintTimeNumber(
                    timeSignature.getNumerator(),
                    new PixelPoint(center.x, center.y - dy));

                // Paint denominator
                paintTimeNumber(
                    timeSignature.getDenominator(),
                    new PixelPoint(center.x, center.y + dy));
            }
        } catch (InvalidTimeSignature ex) {
            logger.warning("Invalid time signature", ex);
        } catch (ConcurrentModificationException ignored) {
        } catch (Exception ex) {
            timeSignature.addError(
                timeSignature.getGlyphs().iterator().next(),
                "Error painting timeSignature " + ex);
        }

        return true;
    }

    //-------------//
    // visit Wedge //
    //-------------//
    @Override
    public boolean visit (Wedge wedge)
    {
        try {
            if (wedge.isStart()) {
                final PixelRectangle box = wedge.getGlyph()
                                                .getContourBox();

                PixelPoint           single;
                PixelPoint           top;
                PixelPoint           bot;

                if (wedge.getShape() == Shape.CRESCENDO) {
                    single = new PixelPoint(box.x, box.y + (box.height / 2));
                    top = new PixelPoint(box.x + box.width, box.y);
                    bot = new PixelPoint(box.x + box.width, box.y + box.height);
                } else {
                    single = new PixelPoint(
                        box.x + box.width,
                        box.y + (box.height / 2));
                    top = new PixelPoint(box.x, box.y);
                    bot = new PixelPoint(box.x, box.y + box.height);
                }

                paintLine(single, top);
                paintLine(single, bot);
            }
        } catch (ConcurrentModificationException ignored) {
        } catch (Exception ex) {
            logger.warning(
                getClass().getSimpleName() + " Error visiting " + wedge,
                ex);
        }

        return true;
    }

    //--------------------//
    // accidentalLocation //
    //--------------------//
    /**
     * Report the precise location to be used for a given accidental sigh
     * (with respect to the related note)
     * @param note the related note
     * @param accidental the accidental glyph
     * @return the location to be used for painting
     */
    protected abstract PixelPoint accidentalLocation (Note  note,
                                                      Glyph accidental);

    //----------//
    // braceBox //
    //----------//
    /**
     * Report the precise box to be used for a given brace
     * @param part the related part
     * @return the box to be used for painting
     */
    protected abstract PixelRectangle braceBox (SystemPart part);

    //--------------//
    // noteLocation //
    //--------------//
    /**
     * Report the precise location to be used for a given note
     * @param note the related note
     * @return the location to be used for painting
     */
    protected abstract PixelPoint noteLocation (Note note);

    //-----------------//
    // getKeySigItemDx //
    //-----------------//
    /**
     * Report the theoretical abscissa gap between items of one key signature
     * (the individual sharp or flat signs)
     * @return the theoretical dx between items
     */
    protected int getKeySigItemDx ()
    {
        return scale.toPixels(constants.keySigItemDx);
    }

    //-------------//
    // basicLayout //
    //-------------//
    /**
     * Build a TextLayout from a String of BasicFont characters (transformed by
     * the provided AffineTransform if any)
     * @param str the string of proper codes
     * @param fat potential affine transformation
     * @return the (sized) TextLayout ready to be drawn
     */
    protected TextLayout basicLayout (String          str,
                                      AffineTransform fat)
    {
        FontRenderContext frc = g.getFontRenderContext();
        Font              font = (fat == null) ? basicFont
                                 : basicFont.deriveFont(fat);

        return new TextLayout(str, font, frc);
    }

    //----------------//
    // initParameters //
    //----------------//
    /**
     * Initialization sequence common to ScorePhysicalPainter and
     * ScoreLogicalPainter
     */
    protected void initParameters ()
    {
        // Determine staff lines parameters
        lineThickness = scale.mainFore();
        lineStroke = new BasicStroke(
            lineThickness,
            BasicStroke.CAP_ROUND,
            BasicStroke.JOIN_ROUND);

        // Determine stems parameters
        stemThickness = scale.mainFore();
        stemHalfThickness = stemThickness / 2;
        stemStroke = new BasicStroke(
            stemThickness,
            BasicStroke.CAP_ROUND,
            BasicStroke.JOIN_ROUND);

        // Set stroke for lines
        g.setStroke(lineStroke);
    }

    //----------//
    // location //
    //----------//
    /**
     * Build the desired absolute drawing point, the abscissa being adjusted to
     * fit on the provided chord stem, and the ordinate being computed from the
     * pitch position with respect to the containing staff
     * @param sysPoint the (approximate) system-based drawing point
     * @param chord the chord whose stem must be stuck to the (note) symbol
     * @param staff the containing staff
     * @param pitch the pitch position with respect to the staff
     * @return the PixelPoint, as precise as possible in X & Y
     */
    protected PixelPoint location (PixelPoint sysPoint,
                                   Chord      chord,
                                   Staff      staff,
                                   double     pitch)
    {
        return new PixelPoint(
            preciseAbscissa(sysPoint, chord),
            staff.getTopLeft().y + staff.pitchToPixels(pitch));
    }

    //----------//
    // location //
    //----------//
    /**
     * Build the desired absolute drawing point, the abscissa being adjusted to
     * fit on the provided chord stem
     * @param sysPoint the (approximate) system-based drawing point
     * @param chord the chord whose stem must be stuck to the (note) symbol
     * @return the PixelPoint, as precise as possible in X
     */
    protected PixelPoint location (PixelPoint sysPoint,
                                   Chord      chord)
    {
        return new PixelPoint(preciseAbscissa(sysPoint, chord), sysPoint.y);
    }

    //----------//
    // location //
    //----------//
    /**
     * Build the desired absolute drawing point, the ordinate being computed
     * from the pitch position with respect to the containing staff
     * @param sysPoint the (approximate) system-based drawing point
     * @param staff the containing staff
     * @param pitch the pitch position with respect to the staff
     * @return the PixelPoint, as precise as possible in Y
     */
    protected PixelPoint location (PixelPoint sysPoint,
                                   Staff      staff,
                                   double     pitch)
    {
        return new PixelPoint(
            sysPoint.x,
            staff.getTopLeft().y + staff.pitchToPixels(pitch));
    }

    //-------//
    // paint //
    //-------//
    /**
     * This is the general paint method for drawing a symbol layout, at a
     * specified location, using a specified alignment
     * @param layout what: the symbol, perhaps transformed
     * @param location where: the precise location in the display
     * @param alignment how: the way the symbol is aligned wrt the location
     */
    protected void paint (TextLayout layout,
                          PixelPoint location,
                          Alignment  alignment)
    {
        OmrFont.paint(g, layout, location, alignment);
    }

    //-------//
    // paint //
    //-------//
    /**
     * A convenient painting method, using default alignment (CENTER + MIDDLE)
     * @param layout what: the symbol, perhaps transformed
     * @param location where: the precise location in the display
     */
    protected void paint (TextLayout layout,
                          PixelPoint location)
    {
        paint(layout, location, defaultAlignment);
    }

    //-------//
    // paint //
    //-------//
    /**
     * Paint a symbol
     * @param shape the symbol shape
     * @param location the precise location
     * @param alignment alignment wrt the symbol
     */
    protected void paint (Shape      shape,
                          PixelPoint location,
                          Alignment  alignment)
    {
        ShapeSymbol symbol = Symbols.getSymbol(shape);

        if (symbol != null) {
            symbol.paintSymbol(g, musicFont, location, alignment);
        }
    }

    //-------//
    // paint //
    //-------//
    /**
     * Paint a symbol with default alignment
     * @param shape the symbol shape
     * @param location the precise location
     */
    protected void paint (Shape      shape,
                          PixelPoint location)
    {
        paint(shape, location, defaultAlignment);
    }

    //-----------//
    // paintLine //
    //-----------//
    /**
     * Draw a line from one PixelPoint to another PixelPoint
     *
     * @param from first point
     * @param to second point
     */
    protected void paintLine (PixelPoint from,
                              PixelPoint to)
    {
        if ((from != null) && (to != null)) {
            g.drawLine(from.x, from.y, to.x, to.y);
        } else {
            logger.warning("line not painted due to null reference");
        }
    }

    //-----------------//
    // paintTimeNumber //
    //-----------------//
    /**
     * Paint a (time) number using the coordinates in units of its center point
     * within the containing system part
     *
     * @param number the number whose icon must be painted
     * @param center the center of desired location
     */
    protected void paintTimeNumber (int        number,
                                    PixelPoint center)
    {
        int[]  codes = ShapeSymbol.numberCodes(number);
        String str = new String(codes, 0, codes.length);
        MusicFont.paint(g, musicFont.layout(str), center, AREA_CENTER);
    }

    //-----------------//
    // preciseAbscissa //
    //-----------------//
    /**
     * Compute the rather precise abscissa, adjacent to the provided chord stem,
     * on the side implied by the specified approximate sysPoint
     * @param sysPoint the (note) approximate center
     * @param chord the chord/stem the note should be stuck to
     * @return the precise value for x
     */
    protected int preciseAbscissa (PixelPoint sysPoint,
                                   Chord      chord)
    {
        // Compute symbol abscissa according to chord stem
        int    stemX = chord.getTailLocation().x;
        double dx = stemHalfThickness - 2d; // slight adjustment

        if (sysPoint.x < stemX) {
            // Symbol is on left side of stem
            return (int) (stemX - dx);
        } else {
            // Symbol is on right side of stem
            return (int) (stemX + dx);
        }
    }

    //~ Inner Classes ----------------------------------------------------------

    //-----------//
    // Constants //
    //-----------//
    private static final class Constants
        extends ConstantSet
    {
        //~ Instance fields ----------------------------------------------------

        /** Font for annotations */
        Constant.Integer basicFontSize = new Constant.Integer(
            "points",
            30,
            "Standard font size for annotations");

        /** Alpha parameter for slot axis transparency (0 .. 255) */
        final Constant.Integer slotAlpha = new Constant.Integer(
            "ByteLevel",
            150,
            "Alpha parameter for slot axis transparency (0 .. 255)");

        /** Color for score entities */
        final Constant.Color musicColor = new Constant.Color(
            "#aaffaa",
            "Color for score entities");

        /** dx between items in a key signature */
        final Scale.Fraction keySigItemDx = new Scale.Fraction(
            1.1,
            "dx between items in a key signature");
    }
}