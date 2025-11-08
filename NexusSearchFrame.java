package org.jd.gui.util.nexus.ui;

import org.jd.gui.util.nexus.NexusSearch;

import javax.swing.JFrame;
import java.awt.BorderLayout;
import java.awt.Dimension;

/**
 * We provide a top-level frame hosting a {@link NexusSearchPanel}
 * bound to a given {@link NexusSearch} implementation.
 *
 * This frame is designed for integration into the main application,
 * and should be constructed and displayed by higher-level controllers.
 */
public final class NexusSearchFrame extends JFrame {

    public NexusSearchFrame(NexusSearch search) {
        super("Repository Search");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        NexusSearchPanel panel = new NexusSearchPanel(search);
        panel.setPreferredSize(new Dimension(1000, 600));

        add(panel, BorderLayout.CENTER);
        pack();
        setLocationRelativeTo(null);
    }
}
