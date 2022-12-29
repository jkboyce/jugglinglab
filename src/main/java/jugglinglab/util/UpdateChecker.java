// UpdateChecker.java
//
// Copyright 2002-2022 Jack Boyce and the Juggling Lab contributors

package jugglinglab.util;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.URI;
import java.net.URL;
import java.net.MalformedURLException;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;
import javax.swing.*;

import jugglinglab.core.Constants;


// Background thread that checks online for an updated version of the application.
// If it finds one, open a dialog box that offers to take the user to the
// download location.

public class UpdateChecker extends Thread {
    static final ResourceBundle guistrings = jugglinglab.JugglingLab.guistrings;
    static final ResourceBundle errorstrings = jugglinglab.JugglingLab.errorstrings;


    public UpdateChecker() {
        setPriority(Thread.MIN_PRIORITY);
        start();
    }

    @Override
    public void run() {
        // first download the Juggling Lab home page, looking for the line
        // containing version information
        InputStream is = null;
        InputStreamReader isr = null;
        String line = null;

        try {
            URL url = new URL(Constants.site_URL);
            is = url.openStream();
            isr = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(isr);

            while ((line = br.readLine()) != null) {
                if (line.contains("versionstring"))
                    break;
            }
        } catch (MalformedURLException mue) {
            // handle errors quietly; no big deal if this background operation fails
        } catch (IOException ioe) {
        } finally {
            try {
                if (isr != null) isr.close();
                if (is != null) is.close();
            } catch (IOException ioe) {
            }
        }

        // something went wrong, fail quietly
        if (line == null)
            return;

        // Use regular expression matching to find the span tag with
        // id "versionstring" surrounding the version number string we want
        String pattern = ".*versionstring.*?>(.*?)<.*";
        final String current_version = line.replaceAll(pattern, "$1");
        String running_version = Constants.version;

        if (current_version == null || current_version.length() == 0 ||
                    JLFunc.compareVersions(current_version, running_version) <= 0)
            return;

        try {
            Thread.sleep(3000);

            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    showUpdateBox(current_version);
                }
            });
        } catch (InterruptedException e) {
        }
    }

    protected static void showUpdateBox(String version) {
        final String title = guistrings.getString("New_version_available");
        final JFrame updateBox = new JFrame(title);
        updateBox.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JPanel updatePanel = new JPanel();
        GridBagLayout gb = new GridBagLayout();
        updatePanel.setLayout(gb);

        String template1 = guistrings.getString("New_version_text1");
        Object[] arguments1 = { version };
        JLabel text1 = new JLabel(MessageFormat.format(template1, arguments1));
        text1.setFont(new Font("SansSerif", Font.PLAIN, 14));
        updatePanel.add(text1);
        gb.setConstraints(text1, JLFunc.constraints(GridBagConstraints.LINE_START,0,1,
                                                       new Insets(20,25,0,25)));

        String template2 = guistrings.getString("New_version_text2");
        Object[] arguments2 = { Constants.version };
        JLabel text2 = new JLabel(MessageFormat.format(template2, arguments2));
        text2.setFont(new Font("SansSerif", Font.PLAIN, 14));
        updatePanel.add(text2);
        gb.setConstraints(text2, JLFunc.constraints(GridBagConstraints.LINE_START,0,2,
                                                       new Insets(0,25,0,25)));

        JLabel text3 = new JLabel(guistrings.getString("New_version_text3"));
        text3.setFont(new Font("SansSerif", Font.PLAIN, 14));
        updatePanel.add(text3);
        gb.setConstraints(text3, JLFunc.constraints(GridBagConstraints.LINE_START,0,3,
                                                       new Insets(20,25,5,25)));

        JPanel butp = new JPanel();
        butp.setLayout(new FlowLayout(FlowLayout.LEADING));
        JButton cancelbutton = new JButton(guistrings.getString("Update_cancel"));
        cancelbutton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateBox.dispose();
            }
        });
        butp.add(cancelbutton);

        JButton yesbutton = new JButton(guistrings.getString("Update_yes"));
        yesbutton.setDefaultCapable(true);
        yesbutton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                boolean browse_supported = (Desktop.isDesktopSupported() &&
                                Desktop.getDesktop().isSupported(Desktop.Action.BROWSE));
                boolean browse_problem = false;

                if (browse_supported) {
                    try {
                        Desktop.getDesktop().browse(new URI(Constants.download_URL));
                    } catch (Exception excep) {
                        browse_problem = true;
                    }
                }

                if (!browse_supported || browse_problem) {
                    String template3 = guistrings.getString("Download_message");
                    Object[] arguments3 = { Constants.download_URL };
                    String message = MessageFormat.format(template3, arguments3);
                    new LabelDialog(updateBox, title, message);
                }
                updateBox.dispose();
            }
        });
        butp.add(yesbutton);

        updatePanel.add(butp);
        gb.setConstraints(butp, JLFunc.constraints(GridBagConstraints.LINE_END,0,4,
                                                 new Insets(10,10,10,10)));

        updatePanel.setOpaque(true);
        updateBox.setContentPane(updatePanel);
        updateBox.getRootPane().setDefaultButton(yesbutton);

        Locale loc = Locale.getDefault();
        updateBox.applyComponentOrientation(ComponentOrientation.getOrientation(loc));

        updateBox.pack();
        updateBox.setResizable(false);
        updateBox.setLocationRelativeTo(null);    // center frame on screen
        updateBox.setVisible(true);
    }
}
