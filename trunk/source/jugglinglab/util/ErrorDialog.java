// ErrorDialog.java
//
// Copyright 2002 by Jack Boyce (jboyce@users.sourceforge.net) and others

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

package jugglinglab.util;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;

import jugglinglab.core.*;


public class ErrorDialog implements Runnable {
    static ResourceBundle guistrings;
    static ResourceBundle errorstrings;
    static {
        guistrings = JLLocale.getBundle("GUIStrings");
        errorstrings = JLLocale.getBundle("ErrorStrings");
    }

	Component parent = null;
	String msg = null;
	
	public ErrorDialog(Component parent, String msg) {
		this.parent = parent;
		this.msg = msg;
		
		SwingUtilities.invokeLater(this);
	}
	
	public void run() {
		JOptionPane.showMessageDialog(parent, msg, "Error", JOptionPane.ERROR_MESSAGE);
	}
	
	
    public static void handleException(Exception e) {
        String exmsg1 = errorstrings.getString("Error_internal_part1");
        String exmsg2 = errorstrings.getString("Error_internal_part2");
        String exmsg3 = errorstrings.getString("Error_internal_part3");
        String exmsg4 = errorstrings.getString("Error_internal_part4");
        String exmsg5 = errorstrings.getString("Error_internal_part5");

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        sw.write(errorstrings.getString("Error_internal_msg_part1")+"\n\n");
        sw.write(errorstrings.getString("Error_internal_msg_part2")+"\n"+
                 errorstrings.getString("Error_internal_msg_part3")+"\n\n");
        sw.write("Juggling Lab version: "+Constants.version+"\n\n");
        e.printStackTrace(pw);
        sw.write("\n");
        // System.getProperties().list(pw);

        final JFrame exframe = new JFrame(errorstrings.getString("Error_internal_title"));
        exframe.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        exframe.getContentPane().setLayout(new BorderLayout());

        JPanel exp = new JPanel();
        exframe.getContentPane().add(exp, BorderLayout.PAGE_START);
        GridBagLayout gb = new GridBagLayout();
        exp.setLayout(gb);

        JLabel text1 = new JLabel(exmsg1);
        text1.setFont(new Font("SansSerif", Font.BOLD, 12));
        exp.add(text1);
        gb.setConstraints(text1, make_constraints(GridBagConstraints.LINE_START,0,0,
                                                  new Insets(10,10,0,10)));

        JLabel text2 = new JLabel(exmsg2);
        text2.setFont(new Font("SansSerif", Font.PLAIN, 12));
        exp.add(text2);
        gb.setConstraints(text2, make_constraints(GridBagConstraints.LINE_START,0,1,
                                                  new Insets(10,10,0,10)));

        JLabel text3 = new JLabel(exmsg3);
        text3.setFont(new Font("SansSerif", Font.PLAIN, 12));
        exp.add(text3);
        gb.setConstraints(text3, make_constraints(GridBagConstraints.LINE_START,0,2,
                                                  new Insets(0,10,0,10)));

        JLabel text4 = new JLabel(exmsg4);
        text4.setFont(new Font("SansSerif", Font.PLAIN, 12));
        exp.add(text4);
        gb.setConstraints(text4, make_constraints(GridBagConstraints.LINE_START,0,3,
                                                  new Insets(0,10,0,10)));

        JLabel text5 = new JLabel(exmsg5);
        text5.setFont(new Font("SansSerif", Font.BOLD, 12));
        exp.add(text5);
        gb.setConstraints(text5, make_constraints(GridBagConstraints.LINE_START,0,4,
                                                  new Insets(10,10,10,10)));

        JTextArea dumpta = new JTextArea();
        dumpta.setText(sw.toString());
        dumpta.setCaretPosition(0);
        JScrollPane jsp = new JScrollPane(dumpta);
        jsp.setPreferredSize(new Dimension(400,200));
        exp.add(jsp);
        gb.setConstraints(jsp, make_constraints(GridBagConstraints.CENTER,0,5,
                                                new Insets(10,10,10,10)));

        JPanel butp = new JPanel();
        butp.setLayout(new FlowLayout(FlowLayout.LEADING));
        JButton quitbutton = new JButton(guistrings.getString("Quit"));
        quitbutton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                System.exit(0);
            }
        });
        butp.add(quitbutton);
        JButton okbutton = new JButton(guistrings.getString("Continue"));
        okbutton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                exframe.setVisible(false);
                exframe.dispose();
            }
        });
        butp.add(okbutton);
        exp.add(butp);
        gb.setConstraints(butp, make_constraints(GridBagConstraints.LINE_END,0,6,
                                                 new Insets(10,10,10,10)));

		Locale loc = JLLocale.getLocale();
		exframe.applyComponentOrientation(ComponentOrientation.getOrientation(loc));
		
        exframe.pack();
		exframe.setResizable(false);
        exframe.setVisible(true);
    }
	
	protected static GridBagConstraints make_constraints(int location, int gridx, int gridy, Insets ins) {
        GridBagConstraints gbc = new GridBagConstraints();

        gbc.anchor = location;
        gbc.fill = GridBagConstraints.NONE;
        gbc.gridheight = gbc.gridwidth = 1;
        gbc.gridx = gridx;
        gbc.gridy = gridy;
        gbc.insets = ins;
        gbc.weightx = gbc.weighty = 0.0;
        return gbc;
    }
}
