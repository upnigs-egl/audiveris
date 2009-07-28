//----------------------------------------------------------------------------//
//                                                                            //
//                            S c o r e B o a r d                             //
//                                                                            //
//  Copyright (C) Herve Bitteur 2000-2009. All rights reserved.               //
//  This software is released under the GNU General Public License.           //
//  Please contact users@audiveris.dev.java.net to report bugs & suggestions. //
//----------------------------------------------------------------------------//
//
package omr.score.ui;

import omr.glyph.text.Language;
import omr.glyph.text.tesseract.TesseractOCR;

import omr.log.Logger;

import omr.score.MeasureRange;
import omr.score.Score;
import omr.score.entity.ScorePart;
import omr.score.midi.MidiAbstractions;

import omr.selection.UserEvent;

import omr.step.Step;

import omr.ui.*;
import omr.ui.field.LField;
import omr.ui.field.LIntegerField;
import omr.ui.util.Panel;

import omr.util.Worker;

import com.jgoodies.forms.builder.*;
import com.jgoodies.forms.layout.*;

import java.awt.event.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.*;

/**
 * Class <code>ScoreBoard</code> is a board that manages score information as
 * both a display and possible input from user.
 *
 * @author Herv&eacute; Bitteur
 * @version $Id$
 */
public class ScoreBoard
    extends Board
{
    //~ Static fields/initializers ---------------------------------------------

    /** Usual logger utility */
    private static final Logger logger = Logger.getLogger(ScoreBoard.class);

    //~ Instance fields --------------------------------------------------------

    /** The related score */
    private final Score score;

    /** Needed for reference from score pane */
    private final MidiPane midiPane;

    /** Collection of individual data panes */
    private final List<Pane> panes = new ArrayList<Pane>();

    //~ Constructors -----------------------------------------------------------

    //------------//
    // ScoreBoard //
    //------------//
    /**
     * Create a ScoreBoard
     *
     * @param unitName name of the unit which declares a score board
     * @param score the related score
     */
    public ScoreBoard (String unitName,
                       Score  score)
    {
        super(unitName + "-ScoreBoard", null, null);
        this.score = score;

        // Sequence of Pane instances
        panes.add(new LanguagePane());
        panes.add(midiPane = new MidiPane());
        panes.add(new ScorePane());
        panes.add(new MeasurePane());

        // Layout
        defineLayout();
    }

    //~ Methods ----------------------------------------------------------------

    //--------//
    // commit //
    //--------//
    /**
     * Check the values and commit them if all are OK
     *
     * @return true if committed, false otherwise
     */
    public boolean commit ()
    {
        if (dataIsValid()) {
            for (Pane pane : panes) {
                pane.commit();
            }

            return true;
        } else {
            return false;
        }
    }

    //---------//
    // onEvent //
    //---------//
    public void onEvent (UserEvent event)
    {
        // void
    }

    //-------------//
    // dataIsValid //
    //-------------//
    /**
     * Make sure every user-entered data is valid
     * @return true if every entry is valid
     */
    private boolean dataIsValid ()
    {
        for (Pane pane : panes) {
            if (!pane.isValid()) {
                return false;
            }
        }

        return true;
    }

    //--------------//
    // defineLayout //
    //--------------//
    private void defineLayout ()
    {
        FormLayout   layout = Panel.makeFormLayout(
            6 + (3 * score.getPartList().size()),
            3);
        PanelBuilder builder = new PanelBuilder(layout, getComponent());
        builder.setDefaultDialogBorder();

        CellConstraints cst = new CellConstraints();
        int             r = 1;

        for (Pane pane : panes) {
            if (pane.getLabel() != null) {
                builder.addSeparator(pane.getLabel(), cst.xyw(1, r, 11));
                r += 2;
            }

            r = 2 + pane.defineLayout(builder, cst, r);
        }
    }

    //~ Inner Classes ----------------------------------------------------------

    //------//
    // Pane //
    //------//
    /**
     * A pane is a sub-component of the ScoreBoard, able to host data, check
     * data validity and apply the requested modifications.
     */
    private abstract static class Pane
    {
        //~ Instance fields ----------------------------------------------------

        /** String used as pane label */
        protected final String label;

        //~ Constructors -------------------------------------------------------

        public Pane (String label)
        {
            this.label = label;
        }

        //~ Methods ------------------------------------------------------------

        /** Report the separator label if any */
        public String getLabel ()
        {
            return label;
        }

        /**
         * Build the related user interface
         * @param builder the shared panel builder
         * @param cst the cell constraints
         * @param r initial row value
         * @return final row value
         */
        public abstract int defineLayout (PanelBuilder    builder,
                                          CellConstraints cst,
                                          int             r);

        /** Are all the pane data valid? */
        public boolean isValid ()
        {
            return true; // By default
        }

        /** Commit the modifications */
        public abstract void commit ();
    }

    //--------------//
    // LanguagePane //
    //--------------//
    private class LanguagePane
        extends Pane
    {
        //~ Instance fields ----------------------------------------------------

        /** ComboBox for text language */
        final JComboBox langCombo;

        /** Global language selection */
        final JCheckBox langBox = new JCheckBox();

        //~ Constructors -------------------------------------------------------

        public LanguagePane ()
        {
            super("Text");

            // langBox for global language assignment
            langBox.setText("Set as default");
            langBox.setToolTipText("Check to set language as global default");

            // ComboBox for text language
            langCombo = createLangCombo();
        }

        //~ Methods ------------------------------------------------------------

        @Override
        public boolean isValid ()
        {
            return true;
        }

        @Override
        public void commit ()
        {
            final String item = (String) langCombo.getItemAt(
                langCombo.getSelectedIndex());
            final String code = codeOf(item);

            if (langBox.isSelected()) {
                // Make this language the default
                Language.setDefaultLanguage(code);
            }

            // Text language for this score
            if (!code.equals(score.getLanguage())) {
                score.setLanguage(code);
                new Worker<Void>() {
                        @Override
                        public Void construct ()
                        {
                            score.getSheet()
                                 .getSheetSteps()
                                 .rebuildAfter(
                                Step.VERTICALS,
                                null,
                                null,
                                true);

                            return null;
                        }
                    }.start();
            }
        }

        @Override
        public int defineLayout (PanelBuilder    builder,
                                 CellConstraints cst,
                                 int             r)
        {
            builder.add(langBox, cst.xyw(3, r, 3));

            JLabel textLabel = new JLabel("Language", SwingConstants.RIGHT);
            builder.add(textLabel, cst.xyw(5, r, 3));
            builder.add(langCombo, cst.xyw(9, r, 3));

            return r;
        }

        /** Report the code out of a label */
        private String codeOf (String label)
        {
            return label.substring(0, 3);
        }

        /** Create a combo box filled with supported language items */
        private JComboBox createLangCombo ()
        {
            // Build the item list, only with the supported languages
            List<String> items = new ArrayList<String>();

            for (String code : TesseractOCR.getInstance()
                                           .getSupportedLanguages()) {
                items.add(itemOf(code));
            }

            JComboBox combo = new JComboBox(items.toArray(new String[0]));
            combo.setToolTipText("Dominant language for textual items");

            final String code = (score.getLanguage() != null)
                                ? score.getLanguage()
                                : Language.getDefaultLanguage();
            combo.setSelectedItem(itemOf(code));

            return combo;
        }

        /** Report an item made of code and full name */
        private String itemOf (String code)
        {
            String fullName = Language.nameOf(code);

            if (fullName != null) {
                return code + " - " + fullName;
            } else {
                return code;
            }
        }
    }

    //-------------//
    // MeasurePane //
    //-------------//
    private class MeasurePane
        extends Pane
        implements ItemListener
    {
        //~ Instance fields ----------------------------------------------------

        /** Range selection */
        final JCheckBox rangeBox = new JCheckBox();

        /** First measure Id */
        final LIntegerField firstId = new LIntegerField(
            "first Id",
            "First measure id of measure range");

        /** Last measure Id */
        final LIntegerField lastId = new LIntegerField(
            "last Id",
            "Last measure id of measure range");

        //~ Constructors -------------------------------------------------------

        public MeasurePane ()
        {
            super("Measure range");

            // rangeBox
            rangeBox.setText("Select");
            rangeBox.setToolTipText("Check to enable measure selection");
            rangeBox.addItemListener(this);

            // Default measure range bounds
            MeasureRange range = score.getMeasureRange();

            if (range != null) {
                rangeBox.setSelected(true);
                firstId.setEnabled(true);
                firstId.setValue(range.getFirstId());
                lastId.setEnabled(true);
                lastId.setValue(range.getLastId());
            } else {
                rangeBox.setSelected(false);
                firstId.setEnabled(false);

                if (score.getFirstSystem() != null) {
                    firstId.setValue(
                        score.getFirstSystem().getFirstPart().getFirstMeasure().getId());
                }

                lastId.setEnabled(false);

                if (score.getLastSystem() != null) {
                    lastId.setValue(
                        score.getLastSystem().getLastPart().getLastMeasure().getId());
                }
            }
        }

        //~ Methods ------------------------------------------------------------

        @Override
        public boolean isValid ()
        {
            // Measure range
            if (rangeBox.isSelected() && (score.getLastSystem() != null)) {
                // First Measure
                int maxMeasureId = score.getLastSystem()
                                        .getLastPart()
                                        .getLastMeasure()
                                        .getId();

                if ((firstId.getValue() < 1) ||
                    (firstId.getValue() > maxMeasureId)) {
                    logger.warning(
                        "First measure Id is not within [1.." + maxMeasureId +
                        "]: " + firstId.getValue());

                    return false;
                }

                // Last Measure
                if ((lastId.getValue() < 1) ||
                    (lastId.getValue() > maxMeasureId)) {
                    logger.warning(
                        "Last measure Id is not within [1.." + maxMeasureId +
                        "]: " + lastId.getValue());

                    return false;
                }

                // First & last consistency
                if (firstId.getValue() > lastId.getValue()) {
                    logger.warning(
                        "First measure Id is greater than last measure Id");

                    return false;
                }
            }

            return true;
        }

        @Override
        public void commit ()
        {
            if (rangeBox.isSelected()) {
                score.setMeasureRange(
                    new MeasureRange(
                        score,
                        firstId.getValue(),
                        lastId.getValue()));
            } else {
                score.setMeasureRange(null);
            }
        }

        @Override
        public int defineLayout (PanelBuilder    builder,
                                 CellConstraints cst,
                                 int             r)
        {
            builder.add(rangeBox, cst.xy(3, r));
            builder.add(firstId.getLabel(), cst.xy(5, r));
            builder.add(firstId.getField(), cst.xy(7, r));
            builder.add(lastId.getLabel(), cst.xy(9, r));
            builder.add(lastId.getField(), cst.xy(11, r));

            return r;
        }

        public void itemStateChanged (ItemEvent e)
        {
            if (rangeBox.isSelected()) {
                firstId.setEnabled(true);
                lastId.setEnabled(true);
            } else {
                firstId.setEnabled(false);
                lastId.setEnabled(false);
            }
        }
    }

    //----------//
    // MidiPane //
    //----------//
    private class MidiPane
        extends Pane
    {
        //~ Instance fields ----------------------------------------------------

        /** Tempo */
        final LIntegerField tempo = new LIntegerField(
            "Tempo",
            "Tempo value in number of quarters per minute");

        /** Volume */
        final LIntegerField volume = new LIntegerField("Volume", "Volume");

        //~ Constructors -------------------------------------------------------

        public MidiPane ()
        {
            super("Midi");

            tempo.setValue(
                (score.getTempo() != null) ? score.getTempo()
                                : score.getDefaultTempo());
            volume.setValue(
                (score.getVolume() != null) ? score.getVolume()
                                : score.getDefaultVolume());
        }

        //~ Methods ------------------------------------------------------------

        @Override
        public boolean isValid ()
        {
            if ((tempo.getValue() < 0) || (tempo.getValue() > 1000)) {
                logger.warning("Tempo value should be in 0..1000 range");

                return false;
            }

            if ((volume.getValue() < 0) || (volume.getValue() > 127)) {
                logger.warning("Volume value should be in 0..127 range");

                return false;
            }

            return true;
        }

        @Override
        public void commit ()
        {
            score.setTempo(tempo.getValue());
            score.setVolume(volume.getValue());
        }

        @Override
        public int defineLayout (PanelBuilder    builder,
                                 CellConstraints cst,
                                 int             r)
        {
            builder.add(tempo.getLabel(), cst.xy(5, r));
            builder.add(tempo.getField(), cst.xy(7, r));

            builder.add(volume.getLabel(), cst.xy(9, r));
            builder.add(volume.getField(), cst.xy(11, r));

            return r;
        }
    }

    //----------//
    // PartPane //
    //----------//
    private class PartPane
        extends Panel
    {
        //~ Instance fields ----------------------------------------------------

        /** The underlying model  */
        private final ScorePart scorePart;

        /** Id of the part */
        private final LField id = new LField(
            false,
            "Id",
            "Id of the score part");

        /** Name of the part */
        private LField name = new LField("Name", "Name for the score part");

        /** Midi Instrument */
        private JComboBox midiBox = new JComboBox(
            MidiAbstractions.getProgramNames());

        //~ Constructors -------------------------------------------------------

        //----------//
        // PartPane //
        //----------//
        public PartPane (ScorePart scorePart)
        {
            this.scorePart = scorePart;

            // Let's impose the id!
            id.setText(scorePart.getPid());

            // Initial setting for part name
            name.setText(
                (scorePart.getName() != null) ? scorePart.getName()
                                : scorePart.getDefaultName());

            // Initial setting for part midi program
            int prog = (scorePart.getMidiProgram() != null)
                       ? scorePart.getMidiProgram()
                       : scorePart.getDefaultProgram();
            midiBox.setSelectedIndex(prog - 1);
        }

        //~ Methods ------------------------------------------------------------

        //-----------//
        // checkPart //
        //-----------//
        public boolean checkPart ()
        {
            // Part name
            if (name.getText()
                    .trim()
                    .length() == 0) {
                logger.warning("Please supply a non empty part name");

                return false;
            }

            return true;
        }

        //------------//
        // commitPart //
        //------------//
        public void commitPart ()
        {
            // Part name
            scorePart.setName(name.getText());

            // Part midi program
            scorePart.setMidiProgram(midiBox.getSelectedIndex() + 1);

            // Replicate the score tempo
            scorePart.setTempo(midiPane.tempo.getValue());
        }

        //--------------//
        // defineLayout //
        //--------------//
        private int defineLayout (PanelBuilder    builder,
                                  CellConstraints cst,
                                  int             r)
        {
            builder.addSeparator(
                "Part #" + scorePart.getId(),
                cst.xyw(1, r, 11));

            r += 2; // --

            builder.add(id.getLabel(), cst.xy(5, r));
            builder.add(id.getField(), cst.xy(7, r));

            builder.add(name.getLabel(), cst.xy(9, r));
            builder.add(name.getField(), cst.xy(11, r));

            r += 2; // --

            builder.add(new JLabel("Midi"), cst.xy(5, r));
            builder.add(midiBox, cst.xyw(7, r, 5));

            return r;
        }
    }

    //-----------//
    // ScorePane //
    //-----------//
    private class ScorePane
        extends Pane
    {
        //~ Instance fields ----------------------------------------------------

        /** Map of score part panes */
        private final Map<ScorePart, PartPane> partPanes = new HashMap<ScorePart, PartPane>();

        //~ Constructors -------------------------------------------------------

        public ScorePane ()
        {
            super(null);
        }

        //~ Methods ------------------------------------------------------------

        @Override
        public boolean isValid ()
        {
            // Each score part
            for (ScorePart scorePart : score.getPartList()) {
                PartPane part = partPanes.get(scorePart);

                if ((part != null) && !part.checkPart()) {
                    return false;
                }
            }

            return true;
        }

        @Override
        public void commit ()
        {
            // Each score part
            for (ScorePart scorePart : score.getPartList()) {
                PartPane part = partPanes.get(scorePart);

                if (part != null) {
                    part.commitPart();
                }
            }
        }

        @Override
        public int defineLayout (PanelBuilder    builder,
                                 CellConstraints cst,
                                 int             r)
        {
            boolean empty = true;

            for (ScorePart scorePart : score.getPartList()) {
                if (!empty) {
                    r += 2;
                }

                PartPane partPane = new PartPane(scorePart);
                r = partPane.defineLayout(builder, cst, r);
                partPanes.put(scorePart, partPane);
                builder.add(partPane, cst.xy(1, r));
                empty = false;
            }

            return r;
        }
    }
}
