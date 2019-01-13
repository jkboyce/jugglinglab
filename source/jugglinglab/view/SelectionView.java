// SelectionView.java
//
// Copyright 2018 by Jack Boyce (jboyce@gmail.com) and others

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
import java.awt.event.*;
import javax.swing.*;

import jugglinglab.core.*;
import jugglinglab.jml.*;
import jugglinglab.util.*;


public class SelectionView extends View {
    protected static final int rows = 3;
    protected static final int columns = 3;
    protected static final int count = rows * columns;
    protected static final int center = (count - 1) / 2;

    protected AnimationPanel[] ja;
    protected Mutator mutator;


    public SelectionView(Dimension dim) {
        this.ja = new AnimationPanel[count];
        for (int i = 0; i < count; i++)
            this.ja[i] = new AnimationPanel();

        this.mutator = new Mutator();

        // will probably want this to be a JLayeredPane so that we can draw grid
        // lines etc. on top of the grid of animations
        // https://docs.oracle.com/javase/tutorial/uiswing/components/layeredpane.html

        JPanel pleft = new JPanel() /* {
            @Override
            public void paintComponent(Graphics g) {
                // super.paint(g);

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
        } */ ;

        pleft.setLayout(new GridLayout(rows, columns));
        for (int i = 0; i < count; i++) {
            this.ja[i].setAnimationPanelPreferredSize(dim);
            pleft.add(this.ja[i]);
        }

        pleft.addMouseListener(new MouseAdapter() {
            // will only receive mouseReleased events here when one of the
            // AnimationPanel objects dispatches it to us in its
            // mouseReleased() method.
            @Override
            public void mouseReleased(MouseEvent me) {
                Component c = me.getComponent();
                int num;
                for (num = 0; num < count; num++) {
                    if (c == SelectionView.this.ja[num])
                        break;
                }
                if (num == count)
                    return;
                try {
                    SelectionView.this.restartView(ja[num].getPattern(), null);
                } catch (JuggleExceptionUser jeu) {
                    new ErrorDialog(parent, jeu.getMessage());
                } catch (JuggleExceptionInternal jei) {
                    ErrorDialog.handleFatalException(jei);
                }
            }
        });

        pleft.addMouseMotionListener(new MouseMotionAdapter() {
            // Dispatched here from one of the AnimationPanels when the
            // user drags the mouse for a camera angle change. Copy to the
            // other animations.
            @Override
            public void mouseDragged(MouseEvent me) {
                Component c = me.getComponent();
                int num;
                for (num = 0; num < count; num++) {
                    if (c == SelectionView.this.ja[num])
                        break;
                }
                if (num == count)
                    return;
                double[] ca = ja[num].getCameraAngle();
                for (int i = 0; i < count; i++) {
                    if (i != num)
                        ja[i].setCameraAngle(ca);
                }
            }
        });

        JPanel pright = mutator.getControlPanel();

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

    @Override
    public void restartView() throws JuggleExceptionUser, JuggleExceptionInternal {
        for (int i = 0; i < count; i++)
            ja[i].restartJuggle();
    }

    @Override
    public void restartView(JMLPattern p, AnimationPrefs c) throws
                        JuggleExceptionUser, JuggleExceptionInternal {
        ja[center].restartJuggle(p, c);
        for (int i = 0; i < count; i++) {
            if (i != center) {
                JMLPattern newp = (p == null ? null : mutator.mutatePattern(p));
                ja[i].restartJuggle(newp, c);
            }
        }
    }

    @Override
    public void setAnimationPanelPreferredSize(Dimension d) {
        for (int i = 0; i < count; i++)
            ja[i].setAnimationPanelPreferredSize(d);
    }

    @Override
    public Dimension getAnimationPanelSize() {
        return ja[center].getSize(new Dimension());
    }

    @Override
    public void disposeView() {
        for (int i = 0; i < count; i++)
            ja[i].disposeAnimation();
    }

    @Override
    public JMLPattern getPattern()              { return ja[center].getPattern(); }

    @Override
    public AnimationPrefs getAnimationPrefs()   { return ja[center].getAnimationPrefs(); }

    @Override
    public boolean getPaused()                  { return ja[center].getPaused(); }

    @Override
    public void setPaused(boolean pause) {
        if (ja[center].message == null)
            for (int i = 0; i < count; i++)
                ja[i].setPaused(pause);
    }

    @Override
    public void writeGIF() {
        for (int i = 0; i < count; i++)
            ja[i].writingGIF = true;
        boolean origpause = getPaused();
        setPaused(true);

        Runnable cleanup = new Runnable() {
            @Override
            public void run() {
                setPaused(origpause);
                for (int i = 0; i < count; i++)
                    ja[i].writingGIF = false;
            }
        };

        new View.GIFWriter(parent, ja[center], cleanup);
    }
}
