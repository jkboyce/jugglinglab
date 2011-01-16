// SelectionView.java
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

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import jugglinglab.core.*;
import jugglinglab.jml.*;
import jugglinglab.util.*;


public class SelectionView extends View {
	protected Animator[] ja = null;
	protected Mutator mutator = null;
	protected Dimension dtemp = null;
	
	
    public SelectionView(Dimension dim) {
		this.dtemp = new Dimension();
		
		final JPanel pleft = new JPanel() {
			public void paint(Graphics g) {
				super.paint(g);
				
				ja[0].getSize(dtemp);
				int vline1x = dtemp.width;
				int vline2x = 2 * vline1x;
				int hline1y = dtemp.height;
				int hline2y = 2 * hline1y;
				int w = 3 * vline1x;
				int h = 3 * hline1y;
				
				g.setColor(Color.black);
				g.drawLine(vline1x, hline1y, vline1x, hline2y);
				g.drawLine(vline1x, hline2y, vline2x, hline2y);
				g.drawLine(vline2x, hline2y, vline2x, hline1y);
				g.drawLine(vline2x, hline1y, vline1x, hline1y);
				
				
				int x, y, width;
				Dimension appdim = this.getSize();
				int appWidth = appdim.width;
				int appHeight = appdim.height;
				FontMetrics fm = g.getFontMetrics();
				String message = "Selection view isn't working yet";
				width = fm.stringWidth(message);
				x = (appWidth > width) ? (appWidth-width)/2 : 0;
				y = (appHeight + fm.getHeight()) / 2;
				g.setColor(this.getBackground());
				g.fillRect(x-10, appHeight/2 - fm.getHeight(), width+20, 2*fm.getHeight());
				g.setColor(Color.black);
				g.drawString(message, x, y);
			}
		};
		
		pleft.setLayout(new GridLayout(3,3));
        this.ja = new Animator[9];
		for (int i = 0; i < 9; i++) {
			ja[i] = new Animator();
			ja[i].setJAPreferredSize(dim);
			pleft.add(ja[i]);
		}
		
		pleft.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent me) {
            }

            public void mouseReleased(MouseEvent me) {
				Component c = me.getComponent();
				int num;
				for (num = 0; num < 9; num++) {
					if (c == SelectionView.this.ja[num])
						break;
				}
				if (num == 9)
					return;
                try {
					SelectionView.this.restartView(ja[num].getPattern(), null);
				} catch (final JuggleExceptionUser jeu) {
					SwingUtilities.invokeLater(new Runnable() {
						public void run() {
							new ErrorDialog(SelectionView.this, jeu.getMessage());
						}
					});
				} catch (final JuggleExceptionInternal jei) {
					ErrorDialog.handleException(jei);
				}
            }
        });
		
		this.mutator = new Mutator();
		final JPanel pright = mutator.getControlPanel();

        GridBagLayout gb = new GridBagLayout();
        this.setLayout(gb);
        
		this.add(pleft);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.LINE_START;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridheight = gbc.gridwidth = 1;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = gbc.weighty = 1.0;
        gb.setConstraints(pleft, gbc);
		
		this.add(pright);
        gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.FIRST_LINE_START;
        gbc.fill = GridBagConstraints.NONE;
        gbc.gridheight = gbc.gridwidth = 1;
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 0.0;
		gbc.weighty = 1.0;
        gb.setConstraints(pright, gbc);
    }

    public void restartView() throws JuggleExceptionUser, JuggleExceptionInternal {
        for (int i = 0; i < 9; i++)
			ja[i].restartJuggle();
    }

    public void restartView(JMLPattern p, AnimatorPrefs c) throws JuggleExceptionUser, JuggleExceptionInternal {
		ja[4].restartJuggle(p, c);
        for (int i = 0; i < 9; i++) {
			if (i != 4) {
				JMLPattern newpat = mutator.mutatePattern(p);
				ja[i].restartJuggle(newpat, c);
			}
		}
    }

    public Dimension getAnimatorSize() {
        return ja[4].getSize(new Dimension());
    }

    public void dispose() {
        for (int i = 0; i < 9; i++)
			ja[i].dispose();
    }

	public JMLPattern getPattern() { return ja[4].getPattern(); }
	
    public boolean getPaused() { return ja[4].getPaused(); }

    public void setPaused(boolean pause) {
        /*
		if (ja[4].message == null)
			for (int i = 0; i < 9; i++)
				ja[i].setPaused(pause);
		*/
    }
}
