// EditView.java
//
// Copyright 2004 by Jack Boyce (jboyce@users.sourceforge.net) and others

/*
    This file is part of Juggling Lab.

    Juggling Lab is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    Juggling Lab is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with Juggling Lab; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package jugglinglab.view;

import java.awt.*;
import java.util.*;
import javax.swing.*;
import javax.swing.border.*;

import jugglinglab.core.*;
import jugglinglab.jml.*;
import jugglinglab.util.*;


public class EditView extends View {
    protected AnimatorEdit jae = null;
    protected JPanel ladder = null;

    static protected int ladder_width = 150;
    static protected int ladder_min_width = 80;


    public EditView(Dimension dim) {
        this.setLayout(new BorderLayout());

        this.jae = new AnimatorEdit();
        jae.setJAPreferredSize(dim);

        this.ladder = new JPanel();
        ladder.setLayout(new BorderLayout());
        ladder.setPreferredSize(new Dimension(ladder_width, 1));
        ladder.setMinimumSize(new Dimension(ladder_min_width, 1));
		ladder.setBackground(Color.white);

		JSplitPane jsp = null;
		Locale loc = JLLocale.getLocale();
		if (ComponentOrientation.getOrientation(loc) == ComponentOrientation.LEFT_TO_RIGHT) {
			jsp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true, jae, ladder);
			jsp.setResizeWeight(1.0);
		} else {
			jsp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true, ladder, jae);
			jsp.setResizeWeight(0.0);
		}
		jsp.setBorder(new EmptyBorder(0,0,0,0));
		jsp.setBackground(Color.white);
		this.setBackground(Color.white);
        this.add(jsp, BorderLayout.CENTER);
    }

    public void restartView() throws JuggleExceptionUser, JuggleExceptionInternal {
        jae.restartJuggle();
    }

    public void restartView(JMLPattern p, AnimatorPrefs c) throws JuggleExceptionUser, JuggleExceptionInternal {
        jae.restartJuggle(p, c);
        if (p != null) {
            LadderDiagram new_ladder;
            /* if (pat.getNumberOfJugglers() > 1) {
                new_ladder = new PassLadderDiagram(pat, parent);
                ((PassLadderDiagram)new_ladder).setAnimator(jae);
            } else {*/
                new_ladder = new EditLadderDiagram(p, parent);
                ((EditLadderDiagram)new_ladder).setAnimator(jae);
            // }

            jae.setLadderDiagram(new_ladder);
            jae.deactivateEvent();
            new_ladder.setPreferredSize(new Dimension(ladder_width, 1));
            new_ladder.setMinimumSize(new Dimension(ladder_min_width, 1));
            this.ladder.removeAll();
            this.ladder.add(new_ladder, BorderLayout.CENTER);
            this.ladder.validate();
        }
    }

    public Dimension getAnimatorSize() {
        return jae.getSize(new Dimension());
    }

    public void dispose() {
        jae.dispose();
    }

	public JMLPattern getPattern() { return jae.getPattern(); }
	
    public boolean getPaused() { return jae.getPaused(); }

    public void setPaused(boolean pause) {
        if (jae.message == null)
            jae.setPaused(pause);
    }
}
