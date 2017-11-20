/*
 * SonarQube Java
 * Copyright (C) 2010-2017 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.plugins.jacoco;

import java.io.File;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.batch.sensor.coverage.CoverageType;
import org.sonar.api.component.ResourcePerspectives;
import org.sonar.api.config.Configuration;
import org.sonar.java.JavaClasspath;
import org.sonar.plugins.java.api.JavaResourceLocator;

import static org.sonar.plugins.jacoco.JaCoCoExtensions.LOG;
import static org.sonar.plugins.jacoco.JacocoConfiguration.IT_REPORT_PATH_PROPERTY;
import static org.sonar.plugins.jacoco.JacocoConfiguration.REPORT_MISSING_FORCE_ZERO;
import static org.sonar.plugins.jacoco.JacocoConfiguration.REPORT_PATHS_PROPERTY;
import static org.sonar.plugins.jacoco.JacocoConfiguration.REPORT_PATH_PROPERTY;

public class JaCoCoSensor implements Sensor {

  private static final String JACOCO_MERGED_FILENAME = "jacoco-merged.exec";
  private final ResourcePerspectives perspectives;
  private final JavaResourceLocator javaResourceLocator;
  private final JavaClasspath javaClasspath;

  public JaCoCoSensor(ResourcePerspectives perspectives, JavaResourceLocator javaResourceLocator, JavaClasspath javaClasspath) {
    this.perspectives = perspectives;
    this.javaResourceLocator = javaResourceLocator;
    this.javaClasspath = javaClasspath;
  }

  @Override
  public void describe(SensorDescriptor descriptor) {
    descriptor.onlyOnLanguage("java").name("JaCoCoSensor");
  }

  @Override
  public void execute(SensorContext context) {
    if (context.config().hasKey(REPORT_MISSING_FORCE_ZERO)) {
      LOG.warn("Property '{}' is deprecated and its value will be ignored.", REPORT_MISSING_FORCE_ZERO);
    }
    Set<File> reportPaths = getReportPaths(context);
    if (reportPaths.isEmpty()) {
      return;
    }
    // Merge JaCoCo reports
    File reportMerged;
    if(reportPaths.size() == 1) {
      reportMerged = reportPaths.iterator().next();
    } else {
      reportMerged = new File(context.fileSystem().workDir(), JACOCO_MERGED_FILENAME);
      reportMerged.getParentFile().mkdirs();
      JaCoCoReportMerger.mergeReports(reportMerged, reportPaths.toArray(new File[0]));
    }
    new UnitTestsAnalyzer(reportMerged).analyse(context);
  }

  private static Set<File> getReportPaths(SensorContext context) {
    Set<File> reportPaths = new HashSet<>();
    Configuration settings = context.config();
    FileSystem fs = context.fileSystem();
    for (String reportPath : settings.getStringArray(REPORT_PATHS_PROPERTY)) {
      File report = fs.resolvePath(reportPath);
      if (!report.isFile()) {
        if (settings.hasKey(REPORT_PATHS_PROPERTY)) {
          LOG.info("JaCoCo report not found: '{}'", reportPath);
        }
      } else {
        reportPaths.add(report);
      }
    }
    Optional<String> reportPathProp = settings.get(REPORT_PATH_PROPERTY);
    if (reportPathProp.isPresent()) {
      String reportPathProperty = reportPathProp.get();
      warnUsageOfDeprecatedProperty(settings, REPORT_PATH_PROPERTY);
      File report = fs.resolvePath(reportPathProperty);
      if (!report.isFile()) {
        LOG.info("JaCoCo UT report not found: '{}'", reportPathProperty);
      } else {
        reportPaths.add(report);
      }
    }
    Optional<String> itReportPathProp = settings.get(IT_REPORT_PATH_PROPERTY);
    if (itReportPathProp.isPresent()) {
      String itReportPathProperty = itReportPathProp.get();
      warnUsageOfDeprecatedProperty(settings, IT_REPORT_PATH_PROPERTY);
      File report = fs.resolvePath(itReportPathProperty);
      if (!report.isFile()) {
        LOG.info("JaCoCo IT report not found: '{}'", itReportPathProperty);
      } else {
        reportPaths.add(report);
      }
    }
    return reportPaths;
  }

  private static void warnUsageOfDeprecatedProperty(Configuration settings, String reportPathProperty) {
    if (!settings.hasKey(REPORT_PATHS_PROPERTY)) {
      LOG.warn("Property '{}' is deprecated. Please use '{}' instead.", reportPathProperty, REPORT_PATHS_PROPERTY);
    }
  }


  class UnitTestsAnalyzer extends AbstractAnalyzer {
    private final File report;

    public UnitTestsAnalyzer(File report) {
      super(perspectives, javaResourceLocator, javaClasspath);
      this.report = report;
    }

    @Override
    protected CoverageType coverageType() {
      return CoverageType.UNIT;
    }

    @Override
    protected File getReport() {
      return report;
    }

  }

  @Override
  public String toString() {
    return getClass().getSimpleName();
  }

}
